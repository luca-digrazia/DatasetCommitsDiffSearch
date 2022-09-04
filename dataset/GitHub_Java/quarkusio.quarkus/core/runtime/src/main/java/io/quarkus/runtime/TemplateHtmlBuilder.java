package io.quarkus.runtime;

public class TemplateHtmlBuilder {

    private static final String HTML_TEMPLATE_START = "" +
            "<!doctype html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "    <title>%1$s%2$s</title>\n" +
            "    <meta charset=\"utf-8\">\n" +
            "    <style>%3$s</style>\n" +
            "</head>\n" +
            "<body>\n";

    private static final String HTML_TEMPLATE_END = "</div></body>\n" +
            "</html>\n";

    private static final String HEADER_TEMPLATE = "<header>\n" +
            "    <h1 class=\"container\">%1$s</h1>\n" +
            "    <div class=\"exception-message\">\n" +
            "        <h2 class=\"container\">%2$s</h2>\n" +
            "    </div>\n" +
            "</header>\n" +
            "<div class=\"container content\">\n";

    private static final String RESOURCES_START = "<div class=\"intro\">%1$s</div><div class=\"resources\">";

    private static final String ANCHOR_TEMPLATE = "<a href=\"/%1$s\">/%2$s</a>";

    private static final String RESOURCE_TEMPLATE = "<h3>%1$s</h3>\n";

    private static final String LIST_START = "<ul>\n";

    private static final String METHOD_START = "<li> %1$s <strong>%2$s</strong>\n"
            + "    <ul>\n";

    private static final String METHOD_IO = "<li>%1$s: %2$s</li>\n";

    private static final String METHOD_END = "    </ul>\n"
            + "</li>";

    private static final String LIST_END = "</ul>\n";

    private static final String RESOURCES_END = "</div>";

    private static final String ERROR_STACK = "    <div class=\"trace\">\n" +
            "        <pre>%1$s</pre>\n" +
            "    </div>\n";

    private static final String ERROR_CSS = "\n" +
            "html, body {\n" +
            "    margin: 0;\n" +
            "    padding: 0;\n" +
            "    font-family: 'Open Sans', Helvetica, Arial, sans-serif;\n" +
            "    font-size: 100%;\n" +
            "    font-weight: 100;\n" +
            "    line-height: 1.4;\n" +
            "}\n" +
            "\n" +
            "html {\n" +
            "    overflow-y: scroll;\n" +
            "}\n" +
            "\n" +
            "body {\n" +
            "    background: #f9f9f9;\n" +
            "}\n" +
            ".container {\n" +
            "    width: 80%;\n" +
            "    margin: 0 auto;\n" +
            "}\n" +
            ".content {\n" +
            "    padding: 1em 0 1em 0;\n" +
            "}\n" +
            "\n" +
            "header, .component-name {\n" +
            "    background-color: #ad1c1c;\n" +
            "}\n" +
            "\n" +
            "ul {\n" +
            "    line-height: 1.5rem;\n" +
            "    margin: 0.25em 0 0.25em 0;\n" +
            "}\n" +
            "\n" +
            ".exception-message {\n" +
            "    background: #be2828;\n" +
            "}\n" +
            "\n" +
            "h1, h2 {\n" +
            "    margin: 0;\n" +
            "    padding: 0;\n" +
            "}\n" +
            "\n" +
            "h1 {\n" +
            "    font-size: 2rem;\n" +
            "    color: #fff;\n" +
            "    line-height: 3.75rem;\n" +
            "    font-weight: 700;\n" +
            "    padding: 0.4rem 0rem 0.4rem 0rem;\n" +
            "}\n" +
            "\n" +
            "h2 {\n" +
            "    font-size: 1.2rem;\n" +
            "    color: rgba(255, 255, 255, 0.85);\n" +
            "    line-height: 2.5rem;\n" +
            "    font-weight: 400;\n" +
            "    padding: 0.4rem 0rem 0.4rem 0rem;\n" +
            "}\n" +
            "\n" +
            ".intro {" +
            "    font-size: 1.2rem;\n" +
            "    font-weight: 400;\n" +
            "    margin: 0.25em 0 1em 0;\n" +
            "}\n" +
            "h3 {\n" +
            "    font-size: 1.2rem;\n" +
            "    line-height: 2.5rem;\n" +
            "    font-weight: 400;\n" +
            "    color: #555;\n" +
            "    margin: 0.25em 0 0.25em 0;\n" +
            "}\n" +
            "\n" +
            ".trace, .resources {\n" +
            "    background: #fff;\n" +
            "    padding: 15px;\n" +
            "    margin: 15px auto;\n" +
            "    border: 1px solid #ececec;\n" +
            "}\n" +
            ".trace {\n" +
            "    overflow-y: scroll;\n" +
            "}\n" +
            "\n" +
            "pre {\n" +
            "    white-space: pre;\n" +
            "    font-family: Consolas, Monaco, Menlo, \"Ubuntu Mono\", \"Liberation Mono\", monospace;\n" +
            "    font-size: 12px;\n" +
            "    line-height: 1.5;\n" +
            "    color: #555;\n" +
            "}\n";

    private StringBuilder result;

    public TemplateHtmlBuilder(String title, String subTitle, String details) {
        result = new StringBuilder(String.format(HTML_TEMPLATE_START, escapeHtml(title),
                subTitle == null || subTitle.isEmpty() ? "" : " - " + escapeHtml(subTitle), ERROR_CSS));
        result.append(String.format(HEADER_TEMPLATE, escapeHtml(title), escapeHtml(details)));
    }

    public TemplateHtmlBuilder stack(String stack) {
        result.append(String.format(ERROR_STACK, escapeHtml(stack)));
        return this;
    }

    public TemplateHtmlBuilder resourcesStart(String title) {
        result.append(String.format(RESOURCES_START, title));
        return this;
    }

    public TemplateHtmlBuilder resourcesEnd() {
        result.append(RESOURCES_END);
        return this;
    }

    public TemplateHtmlBuilder noResourcesFound() {
        result.append(String.format(RESOURCE_TEMPLATE, "No REST resources discovered"));
        return this;
    }

    public TemplateHtmlBuilder resourcePath(String title) {
        return resourcePath(title, true, false);
    }

    public TemplateHtmlBuilder staticResourcePath(String title) {
        return resourcePath(title, false, true);
    }

    public TemplateHtmlBuilder servletMapping(String title) {
        return resourcePath(title, false, false);
    }

    private TemplateHtmlBuilder resourcePath(String title, boolean withListStart, boolean withAnchor) {
        String content;
        if (withAnchor) {
            content = String.format(ANCHOR_TEMPLATE, title, escapeHtml(title));
        } else {
            content = escapeHtml(title);
        }
        result.append(String.format(RESOURCE_TEMPLATE, content));
        if (withListStart) {
            result.append(LIST_START);
        }
        return this;
    }

    public TemplateHtmlBuilder method(String method, String fullPath) {
        result.append(String.format(METHOD_START, escapeHtml(method), escapeHtml(fullPath)));
        return this;
    }

    public TemplateHtmlBuilder consumes(String consumes) {
        result.append(String.format(METHOD_IO, "Consumes", escapeHtml(consumes)));
        return this;
    }

    public TemplateHtmlBuilder produces(String produces) {
        result.append(String.format(METHOD_IO, "Produces", escapeHtml(produces)));
        return this;
    }

    public TemplateHtmlBuilder methodEnd() {
        result.append(METHOD_END);
        return this;
    }

    public TemplateHtmlBuilder resourceStart() {
        result.append(LIST_START);
        return this;
    }

    public TemplateHtmlBuilder resourceEnd() {
        result.append(LIST_END);
        return this;
    }

    @Override
    public String toString() {
        return result.append(HTML_TEMPLATE_END).toString();
    }

    private static String escapeHtml(final String bodyText) {
        if (bodyText == null) {
            return "";
        }

        return bodyText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
