<%@ tag %> <%
    if (!(out instanceof javax.servlet.jsp.tagext.BodyContent)) {
        out.flush();
    }
%> <%-- Use doBody tag this way to indicate we do not want any flushing happening in the body content. --%>
<jsp:doBody var="bodyContent" scope="page" />
${pageScope.bodyContent}