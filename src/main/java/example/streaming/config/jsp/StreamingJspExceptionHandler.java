package example.streaming.config.jsp;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.springframework.web.util.HtmlUtils;

interface StreamingJspExceptionHandler {

    void writeErrorHtml(PrintWriter out, Exception e);


    class MetaRedirectHandler implements StreamingJspExceptionHandler {
        private final String errorPath;
        public MetaRedirectHandler(String errorPath) {
            this.errorPath = errorPath;
        }
        @Override
        public void writeErrorHtml(PrintWriter pw, Exception e) {
            pw.print(String.format("<meta http-equiv=\"refresh\" content=\"0; url=%s\">", errorPath));
        }
    }

    class HtmlDebugHandler implements StreamingJspExceptionHandler {
        private static final String FONT_RESET_CSS =
                "color:#A80000; font-size:12px; font-style:normal; font-variant:normal; "
                        + "font-weight:normal; text-decoration:none; text-transform: none";
        @Override
        public void writeErrorHtml(PrintWriter pw, Exception e) {
            pw.print("<!-- JSP ERROR MESSAGE STARTS HERE -->"
                    + "<!-- ]]> -->"
                    + "<script language=javascript>//\"></script>"
                    + "<script language=javascript>//'></script>"
                    + "<script language=javascript>//\"></script>"
                    + "<script language=javascript>//'></script>"
                    + "</title></xmp></script></noscript></style></object>"
                    + "</head></pre></table>"
                    + "</form></table></table></table></a></u></i></b>"
                    + "<div align='left' "
                    + "style='background-color:#FFFF7C; "
                    + "display:block; border-top:double; padding:4px; margin:0; "
                    + "font-family:Arial,sans-serif; ");
            pw.print(FONT_RESET_CSS);
            pw.print("'>"
                    + "<b style='font-size:12px; font-style:normal; font-weight:bold; "
                    + "text-decoration:none; text-transform: none;'>JSP template error "
                    + " (HTML_DEBUG mode; use META_REDIRECT in production!)</b>"
                    + "<pre style='display:block; background: none; border: 0; margin:0; padding: 0;"
                    + "font-family:monospace; ");
            pw.print(FONT_RESET_CSS);
            pw.println("; white-space: pre-wrap; white-space: -moz-pre-wrap; white-space: -pre-wrap; "
                    + "white-space: -o-pre-wrap; word-wrap: break-word;'>");

            pw.println();
            pw.println("Low-level message: " + e.getClass().getName() + ": " + e.getMessage());
            pw.println();
            pw.println("----");

            String stackTrace = getStackTrace(e);
            pw.println();
            pw.println("Java stack trace (for programmers):");
            pw.println("----");
            pw.println(HtmlUtils.htmlEscape(stackTrace));

            pw.println("</pre></div></html>");
            // Clear page so there's only the error message.
            pw.write(
                    "<script>(self => setTimeout(() =>\n" +
                        "document.body.innerHTML = self.previousElementSibling.outerHTML\n" +
                    "))(document.currentScript);</script>");
        }

        private static String getStackTrace(Exception e) {
            StringWriter stackTraceSw = new StringWriter();
            try (PrintWriter stackPw = new PrintWriter(stackTraceSw)) {
                e.printStackTrace(stackPw);
            }
            return stackTraceSw.toString();
        }

    }

}
