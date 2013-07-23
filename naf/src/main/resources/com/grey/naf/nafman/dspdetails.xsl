<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
<xsl:output method="xml" omit-xml-declaration="yes" indent="no"/>

<xsl:param name="d"/>
<xsl:param name="st"/>
<xsl:param name="v"/>

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
		Internal Dispatcher State
	</div>
	<br/>
	<xsl:element name="a">
		<xsl:attribute name="class">buttonlink</xsl:attribute>
		<xsl:attribute name="href">/</xsl:attribute>
		<span class="infobutton">Home</span>
	</xsl:element>
	&#160;&#160;&#160;&#160;&#160;
	<xsl:element name="a">
		<xsl:attribute name="class">buttonlink</xsl:attribute>
		<xsl:attribute name="href">/nafhome</xsl:attribute>
		<span class="infobutton">NAF Dashboard</span>
	</xsl:element>
	<br/> <br/>
	<xsl:element name="a">
		<xsl:attribute name="class">buttonlink</xsl:attribute>
		<xsl:attribute name="href">DSPSHOW?d=<xsl:value-of select="$d"/>%26st=<xsl:value-of select="$st"/>%26v=<xsl:value-of select="$v"/></xsl:attribute>
		<span class="infobutton">Refresh</span>
	</xsl:element>
	<xsl:apply-templates select="//agents/agent">
		<xsl:sort select="@name"/>
	</xsl:apply-templates>
	<hr class="sectbreak"/>
</body>
</html>
</xsl:template>

<xsl:template match="agent">
	<xsl:variable name="dname" select="@name"/>
	<hr class="sectbreak"/>
	<p>
		<span class="subtitle">
			Dispatcher: <xsl:value-of select="$dname"/>
		</span>
		<br/>
		<xsl:element name="a">
			<xsl:attribute name="class">buttonlink</xsl:attribute>
			<xsl:attribute name="href">STOP?d=<xsl:value-of select="$dname"/>%26st=cmdstatus</xsl:attribute>
			<xsl:attribute name="title">Stop this Dispatcher</xsl:attribute>
			<span class="stopbutton">STOP</span>
		</xsl:element>
	</p>
	<table border="1" cellpadding="10">
		<xsl:apply-templates select="infonodes/infonode">
			<xsl:with-param name="dname" select="$dname"/>
		</xsl:apply-templates>
	</table>
	<br/>
	<xsl:if test="$v!='Y'">
		<xsl:element name="a">
			<xsl:attribute name="class">buttonlink</xsl:attribute>
			<xsl:attribute name="href">DSPSHOW?d=<xsl:value-of select="$dname"/>%26st=<xsl:value-of select="$st"/>%26v=Y</xsl:attribute>
			<xsl:attribute name="title">View this Dispatcher in more verbose detail</xsl:attribute>
			<span class="infobutton">More-Detail</span>
		</xsl:element>
		<br/><br/>
	</xsl:if>
	<xsl:element name="form">
		<xsl:attribute name="method">post</xsl:attribute>
		<xsl:attribute name="action">LOGLEVEL?d=<xsl:value-of select="$dname"/>%26st=cmdstatus</xsl:attribute>
		<input type="submit" class="actbutton" value="New-LogLevel"
			title="Set new logging level for this Dispatcher"/>
		&#160;&#160;
		<select name="log">
			<option value="TRC3" selected="selected">TRC3</option>
			<option value="TRC2">TRC2</option>
			<option value="TRC">TRC</option>
			<option value="INFO">INFO</option>
			<option value="WARN">WARN</option>
			<option value="ERR">ERR</option>
		</select>
	</xsl:element>
	<br/>
	<xsl:element name="form">
		<xsl:attribute name="method">post</xsl:attribute>
		<xsl:attribute name="action">APPSTOP?d=<xsl:value-of select="$dname"/>%26st=cmdstatus</xsl:attribute>
		<input type="submit" class="actbutton" value="Stop-NAFlet" title="Halt a NAFlet"/>
		&#160;&#160;
		<select name="n">
			<option value="" selected="selected">-</option>
			<xsl:apply-templates select="infonodes/infonode[@name='NAFlets']/item" mode="options"/>
		</select>
	</xsl:element>
</xsl:template>

<xsl:template match="infonode">
	<xsl:param name="dname"/>
	<xsl:variable name="nodename" select="@name"/>
	<xsl:variable name="total" select="@total"/>
	<tr>
		<td class="titlecol">
			<xsl:value-of select="$nodename"/>
			<xsl:if test="$total != ''">
				<br/>Total=<xsl:value-of select="$total"/>
			</xsl:if>
		</td>
		<td>
			<xsl:choose>
				<xsl:when test="$nodename='Disposition'">
					<xsl:copy-of select="* | text()"/>
				</xsl:when>
				<xsl:when test="$nodename='NAFlets'">
					<ul>
						<xsl:apply-templates select="item" mode="infonode-naflets"/>
					</ul>
				</xsl:when>
				<xsl:otherwise>
					<xsl:if test="count(item) != 0">
						<ul>
							<xsl:apply-templates select="item" mode="infonode-misclist">
								<xsl:with-param name="dname" select="$dname"/>
							</xsl:apply-templates>
						</ul>
					</xsl:if>
				</xsl:otherwise>
			</xsl:choose>
		</td>
	</tr>
</xsl:template>

<xsl:template match="item" mode="infonode-naflets">
	<li>
		Name: <xsl:value-of select="@id"/>
		<br/>
		Class: <xsl:value-of select="."/>
	</li>
</xsl:template>

<xsl:template match="item" mode="infonode-misclist">
	<xsl:param name="dname"/>
	<li>
		<xsl:copy-of select="* | text()"/>
		<xsl:if test="@cankill">
			&#160;
			<xsl:element name="a">
				<xsl:attribute name="class">buttonlink</xsl:attribute>
				<xsl:attribute name="href">KILLCONN?d=<xsl:value-of select="$dname"/>%26key=<xsl:value-of select="@id"/>%26t=<xsl:value-of select="@time"/>%26st=cmdstatus</xsl:attribute>
				<xsl:attribute name="title">Terminate this connection</xsl:attribute>
				<span class="tinystop">KILL</span>
			</xsl:element>
		</xsl:if>
	</li>
</xsl:template>

<xsl:template match="item" mode="options">
	<xsl:variable name="id" select="@id"/>
	<xsl:element name="option">
		<xsl:attribute name="value"><xsl:value-of select="$id"/></xsl:attribute>
		<xsl:value-of select="$id"/>
	</xsl:element>
</xsl:template>

</xsl:stylesheet>
