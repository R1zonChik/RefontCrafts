package ru.refontstudio.refontcrafts.util;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import ru.refontstudio.refontcrafts.RefontCrafts;

public class ChatLogger extends Handler {
    @Override
    public void publish(LogRecord r) {
        if (r == null) return;
        String m = r.getMessage();
        if (m == null || m.isEmpty()) return;
        ChatLog.send(RefontCrafts.getInstance().prefix() + "&7" + m);
    }
    @Override public void flush() {}
    @Override public void close() throws SecurityException {}
}