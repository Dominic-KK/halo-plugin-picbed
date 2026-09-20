package top.dominickk.picbed.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Attachment.AttachmentSpec;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import top.dominickk.picbed.tools.FileNameGenerator;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 基于 Halo {@link AttachmentHandler} 的 GitHub 图床存储实现。
 *
 * <p>负责把附件上传/删除到指定 GitHub 仓库，并提供公开访问 permalink。</p>
 * <ul>
 *     <li>上传/删除走 GitHub REST API（默认 {@link #DEFAULT_API_BASE}，可用代理覆盖）。</li>
 *     <li>读取(permalink) 走 {@code githubCustomUrl}（通常是 Cloudflare Worker 代理），
 *         仓库为私有时可干净拼接、不携带 token。</li>
 *     <li>Token 存于 Halo Secret，经 {@link ReactiveExtensionClient} 读取，绝不落地 ConfigMap。</li>
 * </ul>
 */
@Slf4j
@Component
public class GitHubAttachmentHandler implements AttachmentHandler {

    public static final String DEFAULT_API_BASE = "https://api.github.com";
    private static final String POLICY_TEMPLATE_NAME = "picbed";
    private static final String OBJECT_KEY_ANNO = "picbed.plugin.halo.run/object-key";
    private static final String SECRET_KEY = "picbed-github-token";

    private static final Scheduler BLOCKING = Schedulers.boundedElastic();
    private static final ObjectMapper JACKSON = new ObjectMapper();

    private final ReactiveExtensionClient client;
    private final HttpClient httpClient;

    public GitHubAttachmentHandler(ReactiveExtensionClient client) {
        this.client = client;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext)
            .filter(ctx -> this.shouldHandle(ctx.policy()))
            .flatMap(ctx -> {
                var props = GithubProperties.from(ctx.configMap());
                return readToken(props).flatMap(token -> upload(ctx, props, token))
                    .map(detail -> this.buildAttachment(detail));
            });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext)
            .filter(ctx -> this.shouldHandle(ctx.policy()))
            .flatMap(ctx -> {
                var annotations = ctx.attachment().getMetadata().getAnnotations();
                if (annotations == null || !annotations.containsKey(OBJECT_KEY_ANNO)) {
                    return Mono.just(ctx.attachment());
                }
                String objectKey = annotations.get(OBJECT_KEY_ANNO);
                var props = GithubProperties.from(ctx.configMap());
                return readToken(props)
                    .flatMap(token -> deleteRemote(props, token, objectKey))
                    .thenReturn(ctx.attachment());
            });
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        var annotations = attachment.getMetadata().getAnnotations();
        if (annotations != null && annotations.containsKey(Constant.EXTERNAL_LINK_ANNO_KEY)) {
            return Mono.just(URI.create(annotations.get(Constant.EXTERNAL_LINK_ANNO_KEY)));
        }
        if (annotations == null || !annotations.containsKey(OBJECT_KEY_ANNO)) {
            return Mono.empty();
        }
        var props = GithubProperties.from(configMap);
        return Mono.just(URI.create(buildPublicUrl(props, annotations.get(OBJECT_KEY_ANNO))));
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment, Policy policy, ConfigMap configMap,
                                  Duration ttl) {
        return getPermalink(attachment, policy, configMap);
    }

    // ------------------------------------------------------------------ upload

    private Mono<ObjectDetail> upload(UploadContext ctx, GithubProperties props, String token) {
        return DataBufferUtils.join(ctx.file().content()).flatMap(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);

            String originalName = ctx.file().filename();
            String objectKey = FileNameGenerator.generateObjectKey(
                props.renameFormat(), originalName, props.githubPathPrefix());
            String mediaType = mediaTypeOf(ctx.file(), originalName);

            return Mono.fromCallable(() -> {
                String apiBase = resolveApiBase(props.githubApiBase());
                String branch = defaultBranch(props.githubBranch());
                String url = apiBase + "/repos/" + props.githubRepo() + "/contents/" + objectKey;

                var body = new LinkedHashMap<String, String>();
                body.put("message", "Upload " + objectKey + " via Picbed Plugin");
                body.put("content", Base64.getEncoder().encodeToString(bytes));
                body.put("branch", branch);

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .PUT(HttpRequest.BodyPublishers.ofString(
                        JACKSON.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200 && response.statusCode() != 201) {
                    throw new RuntimeException("GitHub 上传失败(" + response.statusCode() + "): "
                        + extractError(response));
                }
                log.info("Uploaded {} -> {}/{}", originalName, props.githubRepo(), objectKey);
                return new ObjectDetail(objectKey, originalName, mediaType, bytes.length,
                    buildPublicUrl(props, objectKey));
            }).subscribeOn(BLOCKING);
        });
    }

    // ------------------------------------------------------------------ delete

    private Mono<Void> deleteRemote(GithubProperties props, String token, String objectKey) {
        return Mono.fromCallable(() -> {
            String apiBase = resolveApiBase(props.githubApiBase());
            String branch = defaultBranch(props.githubBranch());
            String repo = props.githubRepo();

            // 1) 先查询文件携带的 SHA
            String getUrl = apiBase + "/repos/" + repo + "/contents/" + objectKey
                + "?ref=" + URLEncoder.encode(branch, StandardCharsets.UTF_8);
            HttpRequest getRequest = HttpRequest.newBuilder().uri(URI.create(getUrl))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .build();
            HttpResponse<String> getResp = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
            if (getResp.statusCode() == 404) {
                // 文件已不存在，视为删除成功（幂等）
                return null;
            }
            if (getResp.statusCode() != 200) {
                throw new RuntimeException("GitHub 查询文件失败(" + getResp.statusCode() + "): "
                    + extractError(getResp));
            }
            var tree = JACKSON.readTree(getResp.body());
            String sha = tree.path("sha").asString(null);
            if (sha == null) {
                return null;
            }

            // 2) 删除文件
            var body = new LinkedHashMap<String, String>();
            body.put("message", "Delete " + objectKey + " via Picbed Plugin");
            body.put("sha", sha);
            body.put("branch", branch);
            String delUrl = apiBase + "/repos/" + repo + "/contents/" + objectKey;
            HttpRequest delRequest = HttpRequest.newBuilder().uri(URI.create(delUrl))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(
                    JACKSON.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> delResp = httpClient.send(delRequest, HttpResponse.BodyHandlers.ofString());
            if (delResp.statusCode() != 200) {
                throw new RuntimeException("GitHub 删除失败(" + delResp.statusCode() + "): "
                    + extractError(delResp));
            }
            log.info("Deleted {}/{}", repo, objectKey);
            return null;
        }).subscribeOn(BLOCKING).then();
    }

    private static String extractError(HttpResponse<String> resp) {
        try {
            var tree = JACKSON.readTree(resp.body());
            var message = tree.path("message").asString(null);
            if (StringUtils.hasText(message)) {
                return message;
            }
        } catch (Exception ignored) {
            // fallthrough
        }
        return resp.body();
    }

    // ------------------------------------------------------------------ helper

    private Mono<String> readToken(GithubProperties props) {
        String secretName = props.githubTokenSecretName();
        if (!StringUtils.hasText(secretName)) {
            return Mono.error(new IllegalArgumentException("GitHub Token 未配置，请在存储策略中填写。"));
        }
        return client.fetch(Secret.class, secretName)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("找不到 Secret: " + secretName)))
            .map(secret -> {
                if (secret.getStringData() != null && secret.getStringData().containsKey(SECRET_KEY)) {
                    String token = secret.getStringData().get(SECRET_KEY);
                    if (StringUtils.hasText(token)) {
                        return token;
                    }
                }
                if (secret.getData() != null && secret.getData().containsKey(SECRET_KEY)) {
                    String token = new String(secret.getData().get(SECRET_KEY), StandardCharsets.UTF_8);
                    if (StringUtils.hasText(token)) {
                        return token;
                    }
                }
                throw new IllegalArgumentException("Secret[" + secretName + "] 中缺少 key: " + SECRET_KEY);
            });
    }

    private Attachment buildAttachment(ObjectDetail detail) {
        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        metadata.setAnnotations(Map.of(
            OBJECT_KEY_ANNO, detail.objectKey(),
            Constant.EXTERNAL_LINK_ANNO_KEY, detail.publicUrl()));

        var spec = new AttachmentSpec();
        spec.setDisplayName(detail.fileName());
        spec.setMediaType(detail.mediaType());
        spec.setSize(detail.size());

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        return attachment;
    }

    private boolean shouldHandle(Policy policy) {
        return policy != null && policy.getSpec() != null
            && POLICY_TEMPLATE_NAME.equals(policy.getSpec().getTemplateName());
    }

    private static String buildPublicUrl(GithubProperties props, String objectKey) {
        String customUrl = props.githubCustomUrl();
        if (StringUtils.hasText(customUrl)) {
            return stripTrailingSlash(customUrl) + "/" + objectKey;
        }
        return "https://raw.githubusercontent.com/" + props.githubRepo() + "/"
            + defaultBranch(props.githubBranch()) + "/" + objectKey;
    }

    private static String resolveApiBase(String base) {
        return StringUtils.hasText(base) ? stripTrailingSlash(base) : DEFAULT_API_BASE;
    }

    private static String stripTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String defaultBranch(String branch) {
        return StringUtils.hasText(branch) ? branch : "main";
    }

    private static String mediaTypeOf(FilePart file, String filename) {
        var contentType = file.headers().getContentType();
        if (contentType != null) {
            return contentType.toString();
        }
        return "application/octet-stream";
    }

    /** GitHub 仓库配置属性，对应存储策略表单 {@code default} 组的各字段。 */
    record GithubProperties(String githubRepo, String githubBranch, String githubPathPrefix,
                            String githubCustomUrl, String githubTokenSecretName,
                            String githubApiBase, String renameFormat) {

        static GithubProperties from(ConfigMap configMap) {
            String json = configMap.getData().getOrDefault("default", "{}");
            try {
                return JACKSON.readValue(json, GithubProperties.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("存储策略配置解析失败: " + json, e);
            }
        }
    }

    record ObjectDetail(String objectKey, String fileName, String mediaType, long size,
                        String publicUrl) {
    }
}