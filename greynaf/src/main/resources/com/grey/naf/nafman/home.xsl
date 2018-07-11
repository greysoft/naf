<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
<xsl:output method="xml" omit-xml-declaration="yes" indent="no"/>

<xsl:variable name="dspnodes" select="//dispatchers/dispatcher"/>

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
			NAF Web Monitor
		</div>
		<p>
			<xsl:element name="a">
				<xsl:attribute name="class">buttonlink</xsl:attribute>
				<xsl:attribute name="href">/</xsl:attribute>
				<span class="infobutton">Home</span>
			</xsl:element>
			&#160;&#160;&#160;&#160;&#160;
			<xsl:element name="a">
				<xsl:attribute name="class">buttonlink</xsl:attribute>
				<xsl:attribute name="href">nafhome</xsl:attribute>
				<span class="infobutton">Refresh</span>
			</xsl:element>
			<br/><br/>
			NAF Version: _SUBTOKEN_NAFVER_
			<br/>
			NAF kernel is running since
			<xsl:value-of select="/nafman/timeboot"/>
			<br/>
			Uptime:
			<xsl:value-of select="/nafman/uptime"/>
			<br/> <br/>
			<xsl:element name="a">
				<xsl:attribute name="class">buttonlink</xsl:attribute>
				<xsl:attribute name="href">STOP?st=cmdstatus</xsl:attribute>
				<xsl:attribute name="title">Stop NAF kernel - halts all Dispatchers</xsl:attribute>
				<span class="stopbutton">STOP</span>
			</xsl:element>
		</p>
		<hr class="sectbreak"/>
		<p>
			<span class="subtitle">Dispatchers</span>
		</p>
		<table border="1" cellpadding="10">
			<tr>
				<th>Name</th>
				<th>Disposition</th>
				<th>NAFlets</th>
				<th/>
			</tr>
			<xsl:apply-templates select="$dspnodes"/>
		</table>
		<p>
			Total Dispatchers:&#160;<xsl:value-of select="count($dspnodes)"/>
			&#160;&#160;
			<xsl:element name="a">
				<xsl:attribute name="class">buttonlink</xsl:attribute>
				<xsl:attribute name="href">DSPSHOW?st=dspdetails</xsl:attribute>
				<xsl:attribute name="title">View details of all Dispatchers on a single page</xsl:attribute>
				<span class="infobutton">View-All</span>
			</xsl:element>
		</p>
		<br/>
		<form method="post" action="LOGLEVEL?st=cmdstatus">
			<input type="submit" class="actbutton" value="New-LogLevel"
				title="Set new logging level for all Dispatchers"/>
			&#160;&#160;
			<select name="log">
				<option value="TRC3" selected="selected">TRC3</option>
				<option value="TRC2">TRC2</option>
				<option value="TRC">TRC</option>
				<option value="INFO">INFO</option>
				<option value="WARN">WARN</option>
				<option value="ERR">ERR</option>
			</select>
		</form>
		<hr class="sectbreak"/>
		<p>
			<span class="subtitle">Commands</span>
		</p>
		<p>
			The following commands can be invoked as is, with no further parameters and context.
			<br/>
			The ones marked as green are neutral, ie. they do not alter anything.
		</p>
		<table border="1" cellpadding="10">
			<tr>
				<th>Command</th>
				<th>Category</th>
				<th>Description</th>
			</tr>
			<xsl:apply-templates select="//commands/command">
				<xsl:sort select="@family"/>
				<xsl:sort select="@name"/>
			</xsl:apply-templates>
		</table>
		<hr class="sectbreak"/>
	</body>
</html>
</xsl:template>

<xsl:template match="dispatcher">
<xsl:variable name="nafletnodes" select="naflets/naflet"/>
<xsl:variable name="nafmantype" select="@nafman"/>
<tr>
	<td>
		<xsl:value-of select="@name"/>
	</td>
	<td>
		<span class="propname">NAFMAN</span>:&#160;<xsl:value-of select="$nafmantype"/>
		<br/>
		<span class="propname">DNS</span>:&#160;<xsl:value-of select="@dns"/>
		<br/>
		<span class="propname">Log-Level</span>:&#160;<xsl:value-of select="@log"/>
	</td>
	<td>
		Total:&#160;<xsl:value-of select="count($nafletnodes)"/>
		<xsl:apply-templates select="$nafletnodes">
			<xsl:sort select="@name"/>
		</xsl:apply-templates>
	</td>
	<td>
		<xsl:if test="$nafmantype!='No'">
			<xsl:element name="a">
				<xsl:attribute name="class">buttonlink</xsl:attribute>
				<xsl:attribute name="href">DSPSHOW?d=<xsl:value-of select="@name"/>%26st=dspdetails</xsl:attribute>
				<span class="infobutton">Details</span>
			</xsl:element>
			<br/> <br/>
			<xsl:element name="a">
				<xsl:attribute name="class">buttonlink</xsl:attribute>
				<xsl:attribute name="href">STOP?d=<xsl:value-of select="@name"/>%26st=cmdstatus</xsl:attribute>
				<xsl:attribute name="title">Stop this Dispatcher</xsl:attribute>
				<span class="stopbutton">STOP</span>
			</xsl:element>
		</xsl:if>
	</td>
</tr>
</xsl:template>

<xsl:template match="naflet">
	<br/>
	<xsl:value-of select="@name"/>
</xsl:template>

<xsl:template match="command">
<tr>
	<td>
		<xsl:element name="a">
			<xsl:attribute name="class">buttonlink</xsl:attribute>
			<xsl:attribute name="href"><xsl:value-of select="@name"/>?%26st=<xsl:value-of select="@xsl"/></xsl:attribute>
			<xsl:attribute name="title">Execute command</xsl:attribute>
			<xsl:choose>
				<xsl:when test="@neutral='Y'">
					<span class="infobutton"><xsl:value-of select="@name"/></span>
				</xsl:when>
				<xsl:otherwise>
					<span class="actbutton"><xsl:value-of select="@name"/></span>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:element>
	</td>
	<td>
		<xsl:value-of select="@family"/>
	</td>
	<td>
		<xsl:value-of select="."/>
	</td>
</tr>
</xsl:template>

</xsl:stylesheet>
