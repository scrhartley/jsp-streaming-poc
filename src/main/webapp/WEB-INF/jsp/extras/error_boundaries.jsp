<%@ taglib prefix="tag" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="c"   uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="myErrorFallback">
   <div style="border: 1px solid red">
       This is my custom error fallback variable. <br>
       Much better than the generic one.
   </div>
</c:set>

<c:set var="sharedErrorFallback" scope="request">
    <div style="border: 1px solid DarkMagenta">
        This is my general requestScope error fallback variable. <br>
        This should be picked up automatically when another fallback is not specified.
    </div>
</c:set>

<!DOCTYPE html>
<html>
	<head></head>
	<body>

	    <div>My page!</div>
	    <br><br>


        <tag:errorBoundary>
            <div>${throwsException1}</div>
        </tag:errorBoundary>

        <br><br>

        <tag:errorBoundary fallback="${myErrorFallback}">
            <div>${throwsException2}</div>
        </tag:errorBoundary>

        <br><br>

        <tag:errorBoundary fallback='<div style="border: 1px solid red">HTML string fallback</div>'>
            <div>${throwsException3}</div>
        </tag:errorBoundary>

        <br><br>

        <tag:errorBoundary>
            <jsp:attribute name="fallbackFragment">
                <div style="border: 1px solid red">
                    This is my custom error fallback fragment. <br>
                    Much better than the generic one.
                </div>
            </jsp:attribute>
            <jsp:body><div>${throwsException4}</div></jsp:body>
        </tag:errorBoundary>


        <br><br>
        <div>Page finished!</div>

	</body>
</html>