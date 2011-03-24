package com.baleksan.index.url;

/**
 * @author <a href="mailto:baleksan@yammer-inc.com" boris/>
 */
public class UrlNormalizer {

    public static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }

        url = url.trim().toLowerCase();

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        } else if (url.endsWith("/index.html")) {
            url = url.substring(0, url.length() - "/index.html".length());
        } else if (url.endsWith("/index.htm")) {
            url = url.substring(0, url.length() - "/index.htm".length());
        } else if (url.endsWith("/default.html")) {
            url = url.substring(0, url.length() - "/default.html".length());
        } else if (url.endsWith("/default.htm")) {
            url = url.substring(0, url.length() - "/default.htm".length());
        }

        if (url.startsWith("https://www.")) {
            url = "https://" + url.substring("https://www.".length());
        }

        if (url.startsWith("http://www.")) {
            url = "http://" + url.substring("http://www.".length());
        }

        url = url.replaceAll("/\\.{1,2}/", "/"); // Remove . and .. path elements
        url = url.replaceFirst("(^[^/]+//[^/:]+):80([$/])", "$1$2"); // Remove default http port

        return url;
    }

}
