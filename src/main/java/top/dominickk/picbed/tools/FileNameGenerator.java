package top.dominickk.picbed.tools;

import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code renameFormat} 占位符解析工具，用于生成 GitHub 仓库中的对象键(objectKey)。
 *
 * <p>支持占位符：{y} 年（4 位）、{m} 月、{d} 日、{h} 时（24 小时制）、{i} 分、{s} 秒、
 * {origin} 原文件名（不含扩展名）、{timestamp} Unix 时间戳、{rand:N} N 位随机字符。</p>
 */
public final class FileNameGenerator {

    private static final DateTimeFormatter Y = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter M = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter H = DateTimeFormatter.ofPattern("HH");
    private static final DateTimeFormatter I = DateTimeFormatter.ofPattern("mm");
    private static final DateTimeFormatter S = DateTimeFormatter.ofPattern("ss");
    private static final char[] RAND_CHARS =
        "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern RAND_PATTERN = Pattern.compile("\\{rand:(\\d+)\\}");

    private FileNameGenerator() {
    }

    /**
     * 根据重命名格式与原始文件名生成完整对象键（包含 {@code pathPrefix} 前缀与扩展名）。
     *
     * @param format           重命名格式，例如 {@code {y}/{m}/{d}{h}{i}-{rand:3}}
     * @param originalFilename 原始文件名，例如 {@code avatar.png}
     * @param pathPrefix       仓库内路径前缀，例如 {@code picgo/}，可为空
     * @return 完整对象键，例如 {@code picgo/2026/09/201030-abc.png}
     */
    public static String generateObjectKey(String format, String originalFilename, String pathPrefix) {
        String base = originalFilename;
        String ext = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot > 0) {
            base = originalFilename.substring(0, dot);
            ext = originalFilename.substring(dot);
        }
        String generated = generateBase(format, base);
        String prefix = pathPrefix == null ? "" : pathPrefix;
        prefix = prefix.replace('\\', '/');
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix += "/";
        }
        return sanitize(prefix + generated + ext);
    }

    private static String generateBase(String format, String originBase) {
        if (!StringUtils.hasText(format)) {
            return originBase;
        }
        ZonedDateTime now = ZonedDateTime.now();
        String result = format;
        result = result.replace("{y}", Y.format(now));
        result = result.replace("{m}", M.format(now));
        result = result.replace("{d}", D.format(now));
        result = result.replace("{h}", H.format(now));
        result = result.replace("{i}", I.format(now));
        result = result.replace("{s}", S.format(now));
        result = result.replace("{origin}", originBase);
        result = result.replace("{timestamp}", String.valueOf(System.currentTimeMillis() / 1000));
        Matcher matcher = RAND_PATTERN.matcher(result);
        StringBuilder sb = new StringBuilder(result.length());
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(randomString(Integer.parseInt(matcher.group(1)))));
        }
        matcher.appendTail(sb);
        return sb.toString().replace("//", "/");
    }

    private static String randomString(int length) {
        StringBuilder sb = new StringBuilder(Math.max(1, length));
        for (int i = 0; i < length; i++) {
            sb.append(RAND_CHARS[RANDOM.nextInt(RAND_CHARS.length)]);
        }
        return sb.toString();
    }

    private static String sanitize(String key) {
        return key.replace("//", "/").replace("\\", "/");
    }
}