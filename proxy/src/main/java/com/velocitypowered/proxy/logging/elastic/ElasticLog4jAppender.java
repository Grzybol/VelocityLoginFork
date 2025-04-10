// ElasticLog4jAppender.java
package com.velocitypowered.proxy.logging.elastic;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;

@Plugin(name = "ElasticAppender", category = "Core", elementType = Appender.ELEMENT_TYPE)
public class ElasticLog4jAppender extends AbstractAppender {

    private static LogBuffer logBuffer;

    protected ElasticLog4jAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions);
    }

    public static void setLogBuffer(LogBuffer buffer) {
        logBuffer = buffer;
    }

    @Override
    public void append(LogEvent event) {
        if (logBuffer == null) return;
        try {
            logBuffer.add(event);
        } catch (Exception ex) {
            if (!ignoreExceptions()) {
                LOGGER.error("Error in ElasticLog4jAppender: {}", ex.getMessage(), ex);
            } else {
                LOGGER.debug("Error in ElasticLog4jAppender: {}", ex.getMessage(), ex);
            }
        }
    }

    @PluginFactory
    public static ElasticLog4jAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout
    ) {
        if (name == null) {
            LOGGER.error("No name provided for ElasticLog4jAppender");
            return null;
        }
        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }
        return new ElasticLog4jAppender(name, filter, layout, true);
    }
}