<%@ taglib prefix="tag" uri="custom.tags.experimental" %>
<%@ taglib prefix="c"   uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html>
	<head>
	    <style> [slot] > li { padding-top: 0.5rem; } </style>
	</head>
	<body>

	    <ul>
            <li> My page using multi-suspend loader tag! </li>

            <tag:loader>
                <li> Some random content </li>

                <tag:loadslot dependencies="myData1" fallback='<li> Loading... </li>'>
                    <li> ${myData1} </li>
                </tag:loadslot>

                <li> Content ahoy </li>

                <tag:loadslot dependencies="myData2">
                    <jsp:attribute name="fallbackFragment"> <li> Loading... </li> </jsp:attribute>
                    <jsp:body> <li> ${myData2} </li> </jsp:body>
                </tag:loadslot>

                <li> Some more content </li>
            </tag:loader>
        </ul>

        <div> Page finished! </div>

	</body>
</html>