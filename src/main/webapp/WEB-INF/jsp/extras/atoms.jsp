<%@ taglib prefix="tag" tagdir="/WEB-INF/tags" %>
<!DOCTYPE html>
<html>
	<head>
	    <style> li + li { padding-top: 0.5rem; } </style>
	</head>
	<body>

        <%-- Compare the behaviour of this page to /blocking-futures/basic --%>

	    <ul>
            <li> <div>My page using atom tag!</div> </li>

            <tag:atom>
                <li> <div>${myData}</div> </li>
            </tag:atom>
            <tag:atom>
                <li> <div>${myData2}</div> </li>
            </tag:atom>
        </ul>

        <div>Page finished!</div>


	</body>
</html>
