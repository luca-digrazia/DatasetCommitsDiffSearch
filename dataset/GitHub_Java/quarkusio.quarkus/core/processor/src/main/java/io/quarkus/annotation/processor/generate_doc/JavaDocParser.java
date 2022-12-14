package io.quarkus.annotation.processor.generate_doc;

import static io.quarkus.annotation.processor.generate_doc.DocGeneratorUtil.hyphenate;

import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.github.javaparser.javadoc.description.JavadocDescriptionElement;
import com.github.javaparser.javadoc.description.JavadocInlineTag;

import io.quarkus.annotation.processor.Constants;

final class JavaDocParser {

    private static final Pattern REMOVE_LEADING_SPACE = Pattern.compile("^ ", Pattern.MULTILINE);

    private static final String HASH = "#";
    private static final String STAR = "*";
    private static final String S_NODE = "s";
    private static final String UNDERSCORE = "_";
    private static final String NEW_LINE = "\n";
    private static final String LINK_NODE = "a";
    private static final String BOLD_NODE = "b";
    private static final String BIG_NODE = "big";
    private static final String DEL_NODE = "del";
    private static final String ITALICS_NODE = "i";
    private static final String TEXT_NODE = "#text";
    private static final String UNDERLINE_NODE = "u";
    private static final String NEW_LINE_NODE = "br";
    private static final String PARAGRAPH_NODE = "p";
    private static final String SMALL_NODE = "small";
    private static final String EMPHASIS_NODE = "em";
    private static final String LIST_ITEM_NODE = "li";
    private static final String HREF_ATTRIBUTE = "href";
    private static final String STRIKE_NODE = "strike";
    private static final String SUB_SCRIPT_NODE = "sub";
    private static final String ORDERED_LIST_NODE = "ol";
    private static final String SUPER_SCRIPT_NODE = "sup";
    private static final String UN_ORDERED_LIST_NODE = "ul";

    private static final String INLINE_JAVA_DOC_TAG_FORMAT = "`%s`";

    private static final String BIG_ASCIDOC_STYLE = "[.big]";
    private static final String LINK_ATTRIBUTE_FORMAT = "[%s]";
    private static final String SUB_SCRIPT_ASCIDOC_STYLE = "~";
    private static final String SUPER_SCRIPT_ASCIDOC_STYLE = "^";
    private static final String SMALL_ASCIDOC_STYLE = "[.small]";
    private static final String ORDERED_LIST_ITEM_ASCIDOC_STYLE = " . ";
    private static final String UNORDERED_LIST_ITEM_ASCIDOC_STYLE = " - ";
    private static final String UNDERLINE_ASCIDOC_STYLE = "[.underline]";
    private static final String LINE_THROUGH_ASCIDOC_STYLE = "[.line-through]";

    public String parse(String javadocComment) {
        if (javadocComment == null || javadocComment.trim().isEmpty()) {
            return Constants.EMPTY;
        }

        Javadoc javadoc = StaticJavaParser.parseJavadoc(javadocComment);

        if (isAsciidoc(javadoc)) {
            // it's Asciidoc so we just pass through
            // unfortunately, the Javadoc parser keeps the leading spaces so let's remove them
            return REMOVE_LEADING_SPACE.matcher(javadoc.getDescription().toText()).replaceAll("");
        }

        return htmlJavadocToAsciidoc(javadoc.getDescription());
    }

    private boolean isAsciidoc(Javadoc javadoc) {
        for (JavadocBlockTag blockTag : javadoc.getBlockTags()) {
            if ("asciidoclet".equals(blockTag.getTagName())) {
                return true;
            }
        }
        return false;
    }

    private String htmlJavadocToAsciidoc(JavadocDescription javadocDescription) {
        StringBuilder sb = new StringBuilder();

        for (JavadocDescriptionElement javadocDescriptionElement : javadocDescription.getElements()) {
            if (javadocDescriptionElement instanceof JavadocInlineTag) {
                JavadocInlineTag inlineTag = (JavadocInlineTag) javadocDescriptionElement;
                String content = inlineTag.getContent().trim();
                switch (inlineTag.getType()) {
                    case CODE:
                    case VALUE:
                    case LITERAL:
                    case SYSTEM_PROPERTY:
                        sb.append(String.format(INLINE_JAVA_DOC_TAG_FORMAT, content));
                        break;
                    case LINK:
                    case LINKPLAIN:
                        if (content.startsWith(HASH)) {
                            content = hyphenate(content.substring(1));
                        }
                        sb.append(String.format(INLINE_JAVA_DOC_TAG_FORMAT, content));
                        break;
                    default:
                        sb.append(content);
                        break;
                }
            } else {
                appendHtml(sb, Jsoup.parseBodyFragment(javadocDescriptionElement.toText()));
            }
        }

        return sb.toString().trim();
    }

    private void appendHtml(StringBuilder sb, Node node) {
        for (Node childNode : node.childNodes()) {
            switch (childNode.nodeName()) {
                case PARAGRAPH_NODE:
                    sb.append(NEW_LINE);
                    appendHtml(sb, childNode);
                    break;
                case ORDERED_LIST_NODE:
                case UN_ORDERED_LIST_NODE:
                    appendHtml(sb, childNode);
                    break;
                case LIST_ITEM_NODE:
                    final String marker = childNode.parent().nodeName().equals(ORDERED_LIST_NODE)
                            ? ORDERED_LIST_ITEM_ASCIDOC_STYLE
                            : UNORDERED_LIST_ITEM_ASCIDOC_STYLE;
                    sb.append(NEW_LINE);
                    sb.append(marker);
                    appendHtml(sb, childNode);
                    break;
                case LINK_NODE:
                    final String link = childNode.attr(HREF_ATTRIBUTE);
                    sb.append(link);
                    final StringBuilder caption = new StringBuilder();
                    appendHtml(caption, childNode);
                    sb.append(String.format(LINK_ATTRIBUTE_FORMAT, caption.toString().trim()));
                    break;
                case BOLD_NODE:
                case EMPHASIS_NODE:
                    sb.append(STAR);
                    appendHtml(sb, childNode);
                    sb.append(STAR);
                    break;
                case ITALICS_NODE:
                    sb.append(UNDERSCORE);
                    appendHtml(sb, childNode);
                    sb.append(UNDERSCORE);
                    break;
                case UNDERLINE_NODE:
                    sb.append(UNDERLINE_ASCIDOC_STYLE);
                    sb.append(HASH);
                    appendHtml(sb, childNode);
                    sb.append(HASH);
                    break;
                case SMALL_NODE:
                    sb.append(SMALL_ASCIDOC_STYLE);
                    sb.append(HASH);
                    appendHtml(sb, childNode);
                    sb.append(HASH);
                    break;
                case BIG_NODE:
                    sb.append(BIG_ASCIDOC_STYLE);
                    sb.append(HASH);
                    appendHtml(sb, childNode);
                    sb.append(HASH);
                    break;
                case SUB_SCRIPT_NODE:
                    sb.append(SUB_SCRIPT_ASCIDOC_STYLE);
                    appendHtml(sb, childNode);
                    sb.append(SUB_SCRIPT_ASCIDOC_STYLE);
                    break;
                case SUPER_SCRIPT_NODE:
                    sb.append(SUPER_SCRIPT_ASCIDOC_STYLE);
                    appendHtml(sb, childNode);
                    sb.append(SUPER_SCRIPT_ASCIDOC_STYLE);
                    break;
                case DEL_NODE:
                case S_NODE:
                case STRIKE_NODE:
                    sb.append(LINE_THROUGH_ASCIDOC_STYLE);
                    sb.append(HASH);
                    appendHtml(sb, childNode);
                    sb.append(HASH);
                    break;
                case NEW_LINE_NODE:
                    sb.append(NEW_LINE);
                    break;
                case TEXT_NODE:
                    final TextNode textNode = (TextNode) childNode;
                    sb.append(textNode.text());
                    break;
                default:
                    appendHtml(sb, childNode);
                    break;
            }
        }
    }

}
