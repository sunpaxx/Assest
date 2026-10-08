package th.thaifont;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft วางตัวอักษรทีละตัว ไม่จัดตำแหน่งสระ/วรรณยุกต์ให้ (ไม่มี shaping)
 * คลาสนี้สลับ "พยัญชนะ + สระ/วรรณยุกต์" เป็นตัวอักษรพิเศษในฟอนต์ที่ขยับตำแหน่งไว้ให้แล้ว
 * ตารางกฎอยู่ใน rules.txt (สร้างจากการจัดวางจริงของฟอนต์ Itim)
 */
public final class ThaiShaper {

    // พยัญชนะ ตามด้วยสระบน/ล่าง วรรณยุกต์ ำ ฯลฯ 1-4 ตัว
    private static final Pattern CLUSTER =
            Pattern.compile("[\\u0E01-\\u0E2E][\\u0E31\\u0E33-\\u0E3A\\u0E47-\\u0E4E]{1,4}");

    private final Map<String, String> rules = new HashMap<>();

    public static ThaiShaper load(InputStream in) throws IOException {
        ThaiShaper s = new ThaiShaper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\t");
                s.rules.put(decode(parts[0]), decode(parts[1]));
            }
        }
        return s;
    }

    private static String decode(String hexList) {
        StringBuilder sb = new StringBuilder();
        for (String h : hexList.trim().split(" ")) {
            sb.appendCodePoint(Integer.parseInt(h, 16));
        }
        return sb.toString();
    }

    public int ruleCount() {
        return rules.size();
    }

    private String shapeCluster(String cluster) {
        // หาแบบยาวสุดที่มีกฎก่อน แล้วค่อยลดความยาวลง
        for (int n = cluster.length(); n >= 2; n--) {
            String hit = rules.get(cluster.substring(0, n));
            if (hit != null) return hit + cluster.substring(n);
        }
        return cluster;
    }

    public String shape(String text) {
        Matcher m = CLUSTER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(shapeCluster(m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public Component shape(Component component) {
        return component.replaceText(TextReplacementConfig.builder()
                .match(CLUSTER)
                .replacement((result, builder) -> builder.content(shapeCluster(result.group())))
                .build());
    }
}
