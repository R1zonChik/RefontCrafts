package ru.refontstudio.refontcrafts.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.refontstudio.refontcrafts.RefontCrafts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ConfigUpdater {
    private final RefontCrafts plugin;

    public ConfigUpdater(RefontCrafts plugin) {
        this.plugin = plugin;
    }

    public void writePretty() {
        try {
            plugin.saveDefaultConfig();
            InputStream in = plugin.getResource("config.yml");
            if (in == null) return;
            List<String> tpl = readLines(in);
            File cfgFile = new File(plugin.getDataFolder(), "config.yml");
            if (!cfgFile.exists()) {
                writeLines(cfgFile, tpl);
                return;
            }
            FileConfiguration cfg = plugin.getConfig();
            List<String> out = mergeTemplateWithValues(tpl, cfg);
            backup(cfgFile);
            writeLines(cfgFile, out);
        } catch (Exception ignored) {}
    }

    private List<String> mergeTemplateWithValues(List<String> template, FileConfiguration cfg) {
        List<String> out = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        int i = 0;
        while (i < template.size()) {
            String line = template.get(i);
            String trim = line.trim();
            if (trim.isEmpty() || trim.startsWith("#")) {
                out.add(line);
                i++;
                continue;
            }
            Key k = parseKeyLine(line);
            if (k == null) {
                out.add(line);
                i++;
                continue;
            }
            while (path.size() > k.level) path.removeLast();
            if (k.section) {
                path.addLast(k.key);
                String full = join(path);
                Object val = cfg.get(full);
                if (val instanceof List) {
                    out.add(line);
                    int listIndent = k.indent + 2;
                    List<?> list = (List<?>) val;
                    for (Object o : list) out.add(spaces(listIndent) + "- " + yamlScalar(o));
                    int j = i + 1;
                    while (j < template.size()) {
                        String nl = template.get(j);
                        String nt = nl.trim();
                        if (nt.isEmpty() || nt.startsWith("#")) { j++; continue; }
                        int ni = indentOf(nl);
                        if (ni > k.indent) { j++; continue; }
                        break;
                    }
                    i = j;
                    continue;
                }
                out.add(line);
                i++;
                continue;
            } else {
                String full = buildPathForLeaf(path, k.level, k.key);
                Object val = cfg.get(full);
                if (val == null || val instanceof ConfigurationSection) {
                    out.add(line);
                } else {
                    out.add(spaces(k.indent) + k.key + ": " + yamlScalar(val));
                }
                i++;
            }
        }
        return out;
    }

    private static class Key {
        final int indent;
        final int level;
        final String key;
        final boolean section;
        Key(int indent, int level, String key, boolean section) {
            this.indent = indent;
            this.level = level;
            this.key = key;
            this.section = section;
        }
    }

    private Key parseKeyLine(String line) {
        int indent = indentOf(line);
        int level = indent / 2;
        String body = line.substring(indent);
        int idx = body.indexOf(':');
        if (idx <= 0) return null;
        String k = body.substring(0, idx).trim();
        if (!k.matches("[A-Za-z0-9_]+")) return null;
        String after = body.substring(idx + 1);
        boolean section = after.trim().isEmpty();
        boolean listItem = body.trim().startsWith("- ");
        if (listItem) return null;
        return new Key(indent, level, k, section);
    }

    private int indentOf(String s) {
        int c = 0;
        while (c < s.length() && s.charAt(c) == ' ') c++;
        return c;
    }

    private String spaces(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    private String join(Deque<String> path) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : path) {
            if (!first) sb.append('.');
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }

    private String buildPathForLeaf(Deque<String> path, int level, String leaf) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String p : path) {
            if (i >= level) break;
            if (sb.length() > 0) sb.append('.');
            sb.append(p);
            i++;
        }
        if (sb.length() > 0) sb.append('.');
        sb.append(leaf);
        return sb.toString();
    }

    private String yamlScalar(Object v) {
        if (v instanceof Number) return v.toString();
        if (v instanceof Boolean) return ((Boolean) v) ? "true" : "false";
        if (v instanceof List) {
            List<?> l = (List<?>) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(yamlScalar(l.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        String s = String.valueOf(v);
        String esc = s.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + esc + "\"";
    }

    private List<String> readLines(InputStream in) throws Exception {
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = br.readLine()) != null) out.add(ln);
        }
        return out;
    }

    private void writeLines(File f, List<String> lines) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            for (int i = 0; i < lines.size(); i++) {
                bw.write(lines.get(i));
                bw.write("\n");
            }
        }
    }

    private void backup(File src) {
        try {
            File dst = new File(src.getParentFile(), "config.yml.bak");
            try (FileInputStream is = new FileInputStream(src); FileOutputStream os = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) != -1) os.write(buf, 0, r);
            }
        } catch (Exception ignored) {}
    }
}