package top.dominickk.picbed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Watcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 监听图床存储策略配置（ConfigMap）的变更，检测到仓库/分支被修改时记录告警日志。
 *
 * 策略创建后应避免修改仓库/分支，否则已存在附件与策略配置脱节，可能造成误删或链接失效。
 * 删除安全已由固位注解保证（附件删除按上传时固化的 repo/branch/object-key 定位），
 * 本组件仅作提醒，不阻断修改。
 */
@Slf4j
@Component
public class PolicyConfigWatcher implements Watcher {

    /** 与 {@code setting-picbed.yaml} 中的分组名保持一致。 */
    private static final String GROUP_KEY = "default";
    private static final String REPO_FIELD = "githubRepo";
    private static final String BRANCH_FIELD = "githubBranch";
    private static final ObjectMapper JACKSON = new ObjectMapper();

    public PolicyConfigWatcher(ReactiveExtensionClient client) {
        client.watch(this);
    }

    @Override
    public void onUpdate(Extension oldExtension, Extension newExtension) {
        if (!(oldExtension instanceof ConfigMap oldCm) || !(newExtension instanceof ConfigMap newCm)) {
            return;
        }
        String oldJson = oldCm.getData().getOrDefault(GROUP_KEY, "{}");
        String newJson = newCm.getData().getOrDefault(GROUP_KEY, "{}");
        if (Objects.equals(oldJson, newJson)) {
            return;
        }
        String oldRepo = fieldOf(oldJson, REPO_FIELD);
        String newRepo = fieldOf(newJson, REPO_FIELD);
        if (oldRepo != null && !Objects.equals(oldRepo, newRepo)) {
            log.warn("检测到存储策略「{}」的仓库由「{}」修改为「{}」；已存在附件仍按上传时固化的仓库删除。",
                newCm.getMetadata().getName(), oldRepo, newRepo);
        }
        String oldBranch = fieldOf(oldJson, BRANCH_FIELD);
        String newBranch = fieldOf(newJson, BRANCH_FIELD);
        if (oldBranch != null && !Objects.equals(oldBranch, newBranch)) {
            log.warn("检测到存储策略「{}」的分支由「{}」修改为「{}」；已存在附件仍按上传时固化的分支删除。",
                newCm.getMetadata().getName(), oldBranch, newBranch);
        }
    }

    @Override
    public void dispose() {
        // 无需处理
    }

    @Override
    public boolean isDisposed() {
        return false;
    }

    private static String fieldOf(String json, String field) {
        try {
            JsonNode node = JACKSON.readTree(json);
            return node.path(field).asString(null);
        } catch (Exception e) {
            return null;
        }
    }
}
