<%@ tag trimDirectiveWhitespaces="true" %>
<%
    if (!(out instanceof javax.servlet.jsp.tagext.BodyContent)) {
        out.flush();
    }
%>