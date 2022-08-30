package org.foo.modules.jahia.strava.cache;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.cache.CacheKeyPartGenerator;
import org.jahia.utils.Patterns;
import org.osgi.service.component.annotations.Component;

import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component(service = CacheKeyPartGenerator.class, immediate = true)
public class SessionAttributeCacheKeyPartGenerator implements CacheKeyPartGenerator {
    private static final Pattern SESSION_ATTRIBUTES_REGEXP = Pattern.compile("(.*)(_sa_\\[([^\\]]+)\\]_)(.*)");

    @Override
    public String getKey() {
        return "sessionAttribute";
    }

    @Override
    public String getValue(Resource resource, RenderContext renderContext, Properties properties) {
        String sessionAttributes = properties.getProperty("cache.sessionAttributes");
        if (!StringUtils.isEmpty(sessionAttributes)) {
            return "_sa_" + Arrays.toString(Patterns.COMMA.split(sessionAttributes)) + "_";
        }
        return "";
    }

    @Override
    public String replacePlaceholders(RenderContext renderContext, String keyPart) {
        HttpSession httpSession = renderContext.getRequest().getSession(false);
        Enumeration<String> attributes = httpSession.getAttributeNames();
        String attribute;
        SortedMap<String, Object> qs = new TreeMap<>();
        if (!attributes.hasMoreElements()) {
            return qs.toString();
        }
        Matcher m = SESSION_ATTRIBUTES_REGEXP.matcher(keyPart);
        if (m.matches()) {
            String qsString = m.group(2);
            String[] sessionAttributes = Patterns.COMMA.split(m.group(3));

            for (String sessionAttribute : sessionAttributes) {
                sessionAttribute = sessionAttribute.trim();
                if (sessionAttribute.endsWith("*")) {
                    sessionAttribute = sessionAttribute.substring(0, sessionAttribute.length() - 1);
                    while (attributes.hasMoreElements()) {
                        attribute = attributes.nextElement();
                        if (attribute.startsWith(sessionAttribute)) {
                            qs.put(attribute, httpSession.getAttribute(attribute));
                        }
                    }
                } else if (httpSession.getAttribute(sessionAttribute) != null) {
                    qs.put(sessionAttribute, httpSession.getAttribute(sessionAttribute));
                }
            }
            keyPart = keyPart.replace(qsString, qs.toString());
        }
        return keyPart;
    }
}
