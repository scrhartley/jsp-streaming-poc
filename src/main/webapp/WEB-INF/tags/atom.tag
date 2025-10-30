<%@ tag %><%

    // In case the contained futures are not done yet,
    // flush already finished content while we wait for them.
    if (!(out instanceof javax.servlet.jsp.tagext.BodyContent)) {
        out.flush();
    }

%><%--
  Use doBody tag this way to indicate we do not want any flushing happening in the body content.
--%><jsp:doBody var="bodyContent" scope="page"/>${pageScope.bodyContent}<%

    // If the contained futures were already done, then now can be a good time to flush the result.
    // This was add for unordered items in AsyncModel.
    if (!(out instanceof javax.servlet.jsp.tagext.BodyContent)) {
        out.flush();
    }

%>