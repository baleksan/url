package com.baleksan.index.url;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Regular expressions in this class are attributable to David Schweisguth
 *
 * @author <a href="mailto:baleksan@yammer-inc.com" boris/>
 */
public class UrlExtractor
{
    private static final Logger LOG = LoggerFactory.getLogger(UrlExtractor.class);

    public static interface URLMatchHandler
    {

        /**
         * It is called when new url string is found in the text.
         *
         * @param urlString an URL string matched (+ protocol part)
         * @param start     the start position in the given text
         * @param end       the end position in the given text
         * @return a boolean value which indicates if more URL matches are needed
         */
        boolean matchURL(String urlString, int start, int end);
    }

    /**
     * Determines if the urlString is a url or not
     *
     * @param urlString
     * @return true if the urlString is a url false otherwise
     */
    public static boolean isUrl(String urlString)
    {
        UrlExtractor extractor = new UrlExtractor();
        URLCollectingHandler handler = new URLCollectingHandler(1);
        extractor.matchURLs(urlString, handler);
        return !handler.getURLs().isEmpty();
    }

    public static class URLCollectingHandler implements URLMatchHandler
    {
        private List<URL> urls = new ArrayList<URL>();
        private Set<String> urlStrings = new HashSet<String>();
        private Integer limit;
        private int count;

        public URLCollectingHandler(Integer limit)
        {
            if (limit != null && limit <= 0) {
                throw new IllegalArgumentException("Limit passed in must be greater than 0: " + limit);
            }
            this.limit = limit;
        }

        public boolean matchURL(String urlString, int start, int end)
        {
            if (urlStrings.contains(urlString)) {
                return takeNext();
            }

            urlStrings.add(urlString);

            try {
                URL url = new URL(urlString);
                if (isAccepted(url)) {
                    urls.add(url);
                }
            } catch (MalformedURLException e) {
                LOG.warn("Unable to construct URL from extracted url: " + urlString);
                return takeNext();
            }

            count++;
            return takeNext();
        }

        private boolean isAccepted(URL url)
        {
            return url.getProtocol().matches("https?");
        }

        private boolean takeNext()
        {
            return (limit == null || count < limit);
        }

        public List<URL> getURLs()
        {
            return new ArrayList<URL>(urls);
        }
    }

    // This pattern intentionally matches some things that are not URLs we want to keep but which, if we didn't match
    // them, would be matched in a way we don't want. For example, we match ftp://foo.bar and reject it later so we
    // don't match the foo.bar part.
    // Hostname parts may contain _; although officially illegal, Wikipedia says it's common among Windows hosts.
    // Hostname parts may not begin or end with - or _.
    private static final String GTLDS = "com|edu|gov|int|mil|net|org|arpa|biz|info|name|pro|aero|coop|museum";

    private static final String TWO_PART_NAME =
            // protocol
            "(\\p{Alpha}+://)?" +

                    // Hostname, 1 or more characters. Must not be preceded by a hostname character or . (in which case it would be
                    // a partial match) or an @ (in which case it would be an email address).
                    "(?<![\\w\\-\\.@])\\p{Alnum}(?:[\\w\\-]*\\p{Alnum})?" +

                    // Top-level domain name. Must be one of a known set, since it's too easy for "foo.bar" to show up in text.
                    "\\.(?:" + GTLDS + ")";

    private static final String IP_SEGMENT = "[0-9]{1,3}";

    private static final String THREE_OR_MORE_PART_NAME =
            // protocol
            "(\\p{Alpha}+://)?" +

                    // Hostname. See above.
                    "(?<![\\w\\-\\.@])\\p{Alnum}(?:[\\w\\-]*\\p{Alnum})?" +

                    // Intermediate name parts, 1 or more characters each.
                    "(?:\\.\\p{Alnum}(?:[\\w\\-]*\\p{Alnum})?)+" +

                    // Top-level domain name, 2 or more characters, which leaves out some legal names but also e.g. "C.I.A".
                    // - and _ not allowed. Allows 1-3 digits, for IP addresses.
                    // if the last set is digits, the previous two must be all digits
                    "\\.(?:(?:(?<=(?:(?<=(?<=" + IP_SEGMENT + "\\.)" + IP_SEGMENT + ")\\." + IP_SEGMENT + "\\.))" + IP_SEGMENT +
                    ")" +

                    "|\\p{Alpha}{2}|" + GTLDS + ")";

    private static final String URL =
            // protocol
            "(\\p{Alpha}+://)" +

                    // Hostname, 1 or more characters.
                    "\\p{Alnum}(?:[\\w\\-]*\\p{Alnum})?" +

                    // Intermediate name parts, ditto.
                    "(?:\\.\\p{Alnum}(?:[\\w\\-]*\\p{Alnum})?)*" +

                    // Top-level domain name. See above.
                    "\\.\\p{Alnum}{2,}";

    // See http://www.ietf.org/rfc/rfc3986.txt for legal characters in each URL part.
    // The names of the following variables more or less correspond to the character classes in that RFC.
    private static final String UNRESERVED = "\\p{Alnum}\\-_~";
    private static final String SUB_DELIMS = "!$&'\\(\\)*+,;=";
    private static final String PUNCTUATION = "!\\.,+;\\(\\)'";

    private static final Pattern URL_PATTERN = Pattern.compile(
            // protocol and hostname
            "(?:(?:" + TWO_PART_NAME + ")|(?:" + THREE_OR_MORE_PART_NAME + ")|(?:" + URL + "))" +

                    // port
                    "(?::\\d+)?" +

                    // path
                    "(?:/(?:[/" + UNRESERVED + "$&*=%:@]|(?:[" + PUNCTUATION + "]+[/" + UNRESERVED + "$&*=%:@]))*)?" +

                    // query string
                    "(?:\\?(?:[" + UNRESERVED + "$&*=%:@/?]|(?:[" + PUNCTUATION + "]+[" + UNRESERVED + "$&*=%:@/?]))+)?" +

                    // fragment
                    "(?:#(?:[\\w\\-]|[" + SUB_DELIMS + "%.~:@/?][\\w\\-])*)?" +

                    // next character is end-of-input or non-word, to provide a boundary
                    "(?=$|[^\\w])",

            Pattern.CASE_INSENSITIVE);

    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(ftp|https?)://", Pattern.CASE_INSENSITIVE);


    public List<URL> extractURLs(String content, Integer limit)
    {
        URLCollectingHandler c = new URLCollectingHandler(limit);
        matchURLs(content, c);
        return c.getURLs();
    }

    public void findURLs(String content, URLMatchHandler handler)
    {
        matchURLs(content, handler);
    }

    private void matchURLs(String content, URLMatchHandler handler)
    {
        Matcher m = URL_PATTERN.matcher(content);
        while (m.find()) {
            String urlString = m.group();
            if (containsOnlyDigitsAndDots(urlString)) {
                continue;
            }

            int protocolGroup = 1;
            String protocol = m.group(protocolGroup);
            while (protocolGroup <= 3 && protocol == null) {
                protocol = m.group(protocolGroup++);
            }

            StringBuilder sb = new StringBuilder();

            if (protocol == null) {
                sb.append("http://").append(urlString);
            } else {
                if (!PROTOCOL_PATTERN.matcher(protocol).matches()) {
                    continue; // wrong URL: invalid protocol
                }
                sb.append(protocol.toLowerCase()).append(urlString.substring(protocol.length()));
            }

            // remove the trailing - URL resolves to the same thing
            // and we have less dupes
            if (sb.charAt(sb.length() - 1) == '/') {
                sb.deleteCharAt(sb.length() - 1);
            }

            if (!handler.matchURL(sb.toString(), m.start(), m.end())) {
                break;
            }
        }
    }

    /**
     * Determines if the given string is composed only of digits and/or dots
     * ('.') so we can filter out phone numbers and IP addresses with no
     * protocol (123.123.12.123 vs. http://123.123.12.123).
     *
     * @param potentialUrlString a string we think might be a URL
     * @return True if the given potentialUrlString contains only digits and/or
     *         dots.
     */
    private boolean containsOnlyDigitsAndDots(String potentialUrlString)
    {
        return !Pattern.compile("[^\\d\\.]").matcher(potentialUrlString).find();
    }

    public List<String> extractUrls(String content)
    {
        return null;
    }
}
