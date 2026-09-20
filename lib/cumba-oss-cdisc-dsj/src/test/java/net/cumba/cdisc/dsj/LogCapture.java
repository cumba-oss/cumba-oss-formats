package net.cumba.cdisc.dsj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures what a production class writes through its Lombok {@code @CustomLog} {@code LOGGER}.
 *
 * <p>
 * The project's {@code lombok.config} declares the custom logger as
 * {@code java.lang.System.getLogger(NAME)}. With no {@link System.LoggerFinder} on the classpath
 * the JDK's default finder routes {@link System.Logger} straight to {@code java.util.logging}, so a
 * plain JUL {@link Handler} attached to the logger named after the class sees every record — and
 * the raw message pattern, before parameter substitution.
 * </p>
 *
 * <p>
 * This exists because a warning is not decoration: every message captured here reports a document
 * the parser could not honour as written (columns missing, an attribute after the rows, an
 * unsupported type mapping). Deleting one of those calls is invisible to a test that only checks
 * the returned value, which is exactly what the surviving "removed call to Logger::log" mutants
 * were saying.
 * </p>
 *
 * <p>
 * The logger's level and parent-handler flag are restored on {@link #close()}, and the parent
 * handlers are muted while capturing so an expected warning does not print into the build log.
 * </p>
 */
final class LogCapture implements AutoCloseable
{

    private final Logger jul;

    private final Handler handler;

    private final Level savedLevel;

    private final boolean savedUseParentHandlers;

    private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());

    private LogCapture(Class<?> aClass)
    {
        // Keep a strong reference: JUL holds loggers weakly, so a collected logger would silently
        // drop the handler and every assertion below would then pass vacuously.
        jul = Logger.getLogger(aClass.getName());
        savedLevel = jul.getLevel();
        savedUseParentHandlers = jul.getUseParentHandlers();
        handler = new Handler()
        {

            @Override
            public void publish(LogRecord aRecord)
            {
                records.add(aRecord);
            }


            @Override
            public void flush()
            {
                // nothing buffered
            }


            @Override
            public void close()
            {
                // nothing to release
            }
        };
        handler.setLevel(Level.ALL);
        jul.setLevel(Level.ALL);
        jul.setUseParentHandlers(false);
        jul.addHandler(handler);
    }


    /**
     * Starts capturing the records logged by the given class.
     *
     * @param aClass
     *            the class whose {@code LOGGER} should be captured.
     * @return an open capture; close it to restore the logger.
     */
    static LogCapture on(Class<?> aClass)
    {
        return new LogCapture(aClass);
    }


    /**
     * Takes a snapshot of what has been captured so far.
     *
     * @return the captured records, in order.
     */
    List<LogRecord> records()
    {
        return new ArrayList<>(records);
    }


    /**
     * Reports whether any captured record at the given level carries the given fragment.
     *
     * @param aFragment
     *            a fragment of the raw (un-substituted) message pattern.
     * @param aLevel
     *            the level the record must carry.
     * @return true if at least one captured record at that level carries the fragment.
     */
    boolean logged(Level aLevel, String aFragment)
    {
        for (LogRecord r : records())
        {
            String msg = r.getMessage();
            if (r.getLevel().intValue() == aLevel.intValue() && msg != null
                    && msg.contains(aFragment))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Renders every captured record as text.
     *
     * @return a readable dump of everything captured, for assertion failure messages.
     */
    String dump()
    {
        StringBuilder sb = new StringBuilder();
        for (LogRecord r : records())
        {
            sb.append(r.getLevel()).append(": ").append(r.getMessage()).append('\n');
        }
        return sb.toString();
    }


    @Override
    public void close()
    {
        jul.removeHandler(handler);
        jul.setLevel(savedLevel);
        jul.setUseParentHandlers(savedUseParentHandlers);
    }
}
