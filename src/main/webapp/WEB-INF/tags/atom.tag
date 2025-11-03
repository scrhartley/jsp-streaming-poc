<%@ tag trimDirectiveWhitespaces="true" %>
<%@ attribute name="autoFlush" type="java.lang.Boolean" %>
<%@ taglib prefix="tag" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="c"   uri="http://java.sun.com/jsp/jstl/core" %>

<%--
    In case the contained futures are not done yet,
    flush already finished content while we wait for them.
--%>
<c:if test="${empty autoFlush or autoFlush}">
    <tag:flush />
</c:if>

<%--
    Use doBody tag this way to indicate we do not want any flushing happening in the body content.
--%>
<jsp:doBody var="bodyContent" scope="page"/>${pageScope.bodyContent}