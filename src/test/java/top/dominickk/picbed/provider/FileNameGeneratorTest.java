package top.dominickk.picbed.provider;

import org.junit.jupiter.api.Test;
import top.dominickk.picbed.tools.FileNameGenerator;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FileNameGeneratorTest {

    private static final Pattern DATE_RAND =
        Pattern.compile("^\\d{4}/\\d{2}/\\d{2}\\d{2}\\d{2}-[A-Za-z0-9]{3}");

    @Test
    void generateWithDateRandKeepsExtension() {
        String key = FileNameGenerator.generateObjectKey(
            "{y}/{m}/{d}{h}{i}-{rand:3}", "avatar.png", "picgo/");
        assertThat(key).startsWith("picgo/").endsWith(".png");
        String body = key.substring("picgo/".length(), key.length() - ".png".length());
        assertThat(DATE_RAND.matcher(body).find()).isTrue();
    }

    @Test
    void generateWithoutPrefix() {
        String key = FileNameGenerator.generateObjectKey(
            "{y}/{m}/{d}{h}{i}-{rand:3}", "a.jpg", null);
        assertThat(key).endsWith(".jpg").doesNotStartWith("/");
    }

    @Test
    void generateWithOriginAndTimestampAndRand() {
        String key = FileNameGenerator.generateObjectKey(
            "{y}/{m}/{d}{h}{i}-{origin}-{timestamp}-{rand:4}", "my photo.png", "picgo/");
        String name = key.substring(0, key.length() - ".png".length());
        // 形如 picgo/2026/09/201030-my photo-<10位时间戳>-<4位随机>
        assertThat(name).contains("my photo");
        assertThat(name).matches("^picgo/\\d{4}/\\d{2}/\\d{2}\\d{4}-my photo-\\d{10}-[A-Za-z0-9]{4}$");
    }

    @Test
    void generateNoExtensionKeepsAsIs() {
        String key = FileNameGenerator.generateObjectKey(
            "{y}/{m}/{d}{h}{i}-{rand:3}", "photo", "");
        assertThat(key).doesNotEndWith(".");
    }

    @Test
    void formatNormalizesSlashesAndSpaces() {
        String key = FileNameGenerator.generateObjectKey(
            "{y}/{m}/{d}{h}{i}-{rand:2}", "x.png", "picgo//");
        // pathPrefix 以单斜杠拼接，多斜杠被折叠为单斜杠
        assertThat(key).matches("^picgo/\\d{4}/\\d{2}/\\d{2}\\d{4}-[A-Za-z0-9]{2}\\.png$");
        assertThat(key).doesNotContain("//");
    }
}