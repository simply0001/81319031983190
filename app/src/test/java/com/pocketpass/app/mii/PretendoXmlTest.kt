package com.pocketpass.app.mii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PretendoXmlTest {
    @Test
    fun readsThePidOutOfAMappedIdsReply() {
        val xml = "<?xml version=\"1.0\"?><mapped_ids><mapped_id><in_id>jonbarrow</in_id>" +
            "<out_id>1798888979</out_id></mapped_id></mapped_ids>"

        assertEquals(1798888979L, PretendoXml.parseMappedPid(xml))
    }

    @Test
    fun anUnknownIdMapsToNothing() {
        val xml = "<?xml version=\"1.0\"?><mapped_ids><mapped_id><in_id>nobody-here</in_id>" +
            "<out_id/></mapped_id></mapped_ids>"

        assertNull(PretendoXml.parseMappedPid(xml))
        assertNull(PretendoXml.parseMappedPid("not xml at all"))
        assertNull(PretendoXml.parseMappedPid(""))
    }

    @Test
    fun readsTheMiiRecordWithItsPortrait() {
        val record = requireNotNull(PretendoXml.parseMii(MII_REPLY))

        assertEquals(1234L, record.pid)
        assertEquals("Piip &amp; co", record.name.replace("&", "&amp;"))
        assertEquals(Ver3StoreDataTest.PRETENDO_DEFAULT_MII_BASE64, record.miiDataBase64)
        assertEquals("https://r2-cdn.pretendo.cc/mii/1234/normal_face.png", record.portraitUrl)
    }

    @Test
    fun aMiiWithoutImagesStillParses() {
        val xml = "<?xml version=\"1.0\"?><miis><mii><data>${Ver3StoreDataTest.PRETENDO_DEFAULT_MII_BASE64}</data>" +
            "<id>1</id><name>Solo</name><pid>77</pid><primary>Y</primary><user_id>solo-user</user_id></mii></miis>"

        val record = requireNotNull(PretendoXml.parseMii(xml))

        assertEquals(77L, record.pid)
        assertEquals("Solo", record.name)
        assertNull(record.portraitUrl)
        assertEquals("https://r2-cdn.pretendo.cc/mii/77/normal_face.png", pretendoPortraitUrl(record.pid))
    }

    @Test
    fun repliesWithoutMiiDataAreRejected() {
        assertNull(PretendoXml.parseMii("<?xml version=\"1.0\"?><miis></miis>"))
        assertNull(PretendoXml.parseMii("<?xml version=\"1.0\"?><miis><mii><pid>1</pid></mii></miis>"))
        assertNull(PretendoXml.parseMii("<?xml version=\"1.0\"?><miis><mii><data>AAAA</data></mii></miis>"))
    }

    @Test
    fun errorRepliesSurfaceTheirMessage() {
        val xml = "<?xml version=\"1.0\"?><errors><error><cause>client_id</cause><code>0004</code>" +
            "<message>API application invalid or incorrect application credentials</message></error></errors>"

        assertEquals(
            "API application invalid or incorrect application credentials",
            PretendoXml.errorMessage(xml),
        )
        assertNull(PretendoXml.errorMessage(MII_REPLY))
    }

    @Test
    fun externalEntitiesAreNotResolved() {
        val xml = "<?xml version=\"1.0\"?><!DOCTYPE mapped_ids [<!ENTITY leak SYSTEM \"file:///etc/hostname\">]>" +
            "<mapped_ids><mapped_id><in_id>x</in_id><out_id>&leak;</out_id></mapped_id></mapped_ids>"

        assertNull(PretendoXml.parseMappedPid(xml))
    }

    private companion object {
        val MII_REPLY = "<?xml version=\"1.0\"?><miis><mii>" +
            "<data>${Ver3StoreDataTest.PRETENDO_DEFAULT_MII_BASE64}</data>" +
            "<id>3438773579</id><images>" +
            "<image><cached_url>https://r2-cdn.pretendo.cc/mii/1234/frustrated.png</cached_url><id>3438773579</id>" +
            "<url>https://r2-cdn.pretendo.cc/mii/1234/frustrated.png</url><type>frustrated_face</type></image>" +
            "<image><cached_url>https://r2-cdn.pretendo.cc/mii/1234/normal_face.png</cached_url><id>3438773579</id>" +
            "<url>https://r2-cdn.pretendo.cc/mii/1234/normal_face.png</url><type>normal_face</type></image>" +
            "</images><name>Piip &amp; co</name><pid>1234</pid><primary>Y</primary><user_id>piip</user_id></mii></miis>"
    }
}
