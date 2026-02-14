<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="html" indent="yes" omit-xml-declaration="yes"/>

  <xsl:template match="/">
    <article class="dm-content">
      <h2>
        <xsl:choose>
          <xsl:when test="//*[local-name()='techName'] and //*[local-name()='infoName']">
            <xsl:value-of select="normalize-space(//*[local-name()='techName'][1])"/>
            <xsl:text> - </xsl:text>
            <xsl:value-of select="normalize-space(//*[local-name()='infoName'][1])"/>
          </xsl:when>
          <xsl:otherwise>Quick Preview</xsl:otherwise>
        </xsl:choose>
      </h2>

      <xsl:for-each select="//*[local-name()='descript']/*[local-name()='levelledPara']">
        <section class="dm-section">
          <h3><xsl:value-of select="normalize-space(*[local-name()='title'][1])"/></h3>
          <xsl:for-each select="*[local-name()='para']">
            <p><xsl:apply-templates/></p>
          </xsl:for-each>
        </section>
      </xsl:for-each>

      <xsl:if test="//*[local-name()='proceduralStep']">
        <section class="dm-section dm-procedure">
          <h3>Procedure</h3>
          <ol class="dm-steps">
            <xsl:for-each select="//*[local-name()='proceduralStep']">
              <li>
                <xsl:for-each select="*[local-name()='para']">
                  <p><xsl:apply-templates/></p>
                </xsl:for-each>
                <xsl:for-each select=".//*[local-name()='graphic']">
                  <xsl:variable name="icn">
                    <xsl:choose>
                      <xsl:when test="@infoEntityIdent and normalize-space(@infoEntityIdent) != ''">
                        <xsl:value-of select="normalize-space(@infoEntityIdent)"/>
                      </xsl:when>
                      <xsl:otherwise>
                        <xsl:value-of select="substring-after(normalize-space(@*[local-name()='href']), 'ICN-')"/>
                      </xsl:otherwise>
                    </xsl:choose>
                  </xsl:variable>
                  <button class="dm-inline-link dm-graphic-link" type="button">
                    <xsl:attribute name="data-icn-id">
                      <xsl:choose>
                        <xsl:when test="contains($icn, 'ICN-')"><xsl:value-of select="$icn"/></xsl:when>
                        <xsl:otherwise><xsl:text>ICN-</xsl:text><xsl:value-of select="$icn"/></xsl:otherwise>
                      </xsl:choose>
                    </xsl:attribute>
                    Open image
                  </button>
                </xsl:for-each>
              </li>
            </xsl:for-each>
          </ol>
        </section>
      </xsl:if>
    </article>
  </xsl:template>

  <xsl:template match="*[local-name()='dmRef']">
    <xsl:variable name="href" select="normalize-space(@*[local-name()='href'])"/>
    <xsl:variable name="dmId" select="substring-after($href, 'DMC-')"/>
    <button class="dm-inline-link dm-dm-link" type="button">
      <xsl:attribute name="data-dm-id">
        <xsl:text>DMC-</xsl:text>
        <xsl:value-of select="$dmId"/>
      </xsl:attribute>
      <xsl:text>DMC-</xsl:text><xsl:value-of select="$dmId"/>
    </button>
  </xsl:template>

  <xsl:template match="text()">
    <xsl:value-of select="normalize-space(.)"/>
    <xsl:text> </xsl:text>
  </xsl:template>
</xsl:stylesheet>
