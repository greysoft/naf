<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
<xsl:output method="xml" omit-xml-declaration="yes" indent="no"/>

<xsl:variable name="cmdnodes" select="//commands/command"/>

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
		Registered NAFMAN Handlers
	</div>
	<br/>
	<xsl:element name="a">
		<xsl:attribute name="class">buttonlink</xsl:attribute>
		<xsl:attribute name="href">/</xsl:attribute>
		<span class="infobutton">Home</span>
	</xsl:element>
	<p>
		There is a total of <xsl:value-of select="count($cmdnodes)"/> registered commands,
		with <xsl:value-of select="count(//handlers/handler)"/> handlers.
		<br/>
		An alphabetic listing follows, giving the registered handler objects and their owner Dispatcher.
	</p>
	<xsl:apply-templates select="$cmdnodes">
		<xsl:sort select="@code"/>
	</xsl:apply-templates>
	<hr class="sectbreak"/>
</body>
</html>
</xsl:template>

<xsl:template match="command">
	<xsl:variable name="hnodes" select="handlers/handler"/>
	<hr class="sectbreak"/>
	<p>
		<span class="subtitle">
			Command: <xsl:value-of select="@code"/>
		</span>
		<br/>
		Category: <xsl:value-of select="@family"/>
		<br/>
		<span class="helptext">
			<xsl:value-of select="desc"/>
		</span>
	</p>
	<table border="1" cellpadding="10">
		<xsl:apply-templates select="$hnodes">
			<xsl:sort select="@dispatcher"/>
		</xsl:apply-templates>
	</table>
</xsl:template>

<xsl:template match="handler">
	<tr>
		<td>
			Dispatcher: <xsl:value-of select="@dispatcher"/>
		</td>
		<td>
			<xsl:value-of select="."/>
			<xsl:if test="@pref!=0">
				(preference=<xsl:value-of select="@pref"/>)
			</xsl:if>
		</td>
	</tr>
</xsl:template>

</xsl:stylesheet>
