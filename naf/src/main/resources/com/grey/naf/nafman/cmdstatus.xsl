<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
<xsl:output method="xml" omit-xml-declaration="yes" indent="no"/>

<xsl:variable name="hnodes" select="//handlers/handler"/>

<xsl:template match="/">
<xsl:text disable-output-escaping='yes'>&lt;!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd"&gt;</xsl:text>
<html>
<head>
	<title>NAFMAN-Web</title>
	<link rel="stylesheet" type="text/css" href="nafman.css"/>
	<meta http-equiv="Content-Type" content="text/html;charset=UTF-8"/>
</head>
<body>
	<div class="pagetitle">
		Command Status
	</div>
	<br/>
	<xsl:element name="a">
		<xsl:attribute name="class">buttonlink</xsl:attribute>
		<xsl:attribute name="href">/</xsl:attribute>
		<span class="infobutton">Home</span>
	</xsl:element>
	<p>
		Command was processed by <xsl:value-of select="count($hnodes)"/> handler(s)
	</p>
	<xsl:apply-templates select="$hnodes"/>
	<hr class="sectbreak"/>
</body>
</html>
</xsl:template>

<xsl:template match="handler">
	<hr class="sectbreak"/>
	<p>
		<span class="subtitle">
			<xsl:value-of select="@dname"/>&#160;&#187;&#160;<xsl:value-of select="@hclass"/>&#160;&#187;&#160;<xsl:value-of select="@hname"/>
		</span>
		<br/>
		<xsl:copy-of select="* | text()"/>
	</p>
</xsl:template>

</xsl:stylesheet>
