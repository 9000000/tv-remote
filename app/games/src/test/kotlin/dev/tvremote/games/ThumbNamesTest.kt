package dev.tvremote.games

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbNamesTest {
    @Test fun cleanReplacesRetroArchsCharacters() {
        assertEquals("Q_bert", ThumbNames.clean("Q&bert"))
        assertEquals("A_B_C_D_E_F_G_H_I_J_K", ThumbNames.clean("A&B*C/D:E`F<G>H?I\\J|K"))
        assertEquals("Super Mario Bros. (World)", ThumbNames.clean("Super Mario Bros. (World)"))
    }

    @Test fun candidatesKeepTheExactNameFirst() {
        assertEquals(listOf("Super Mario Bros. (World)"), ThumbNames.candidates("Super Mario Bros. (World).nes"))
        assertEquals(listOf("Zelda - A Link (USA) (Rev 1) [!]", "Zelda - A Link (USA)"), ThumbNames.candidates("Zelda - A Link (USA) (Rev 1) [!].sfc"))
        assertEquals(listOf("Tom & Jerry".replace("&", "_")), ThumbNames.candidates("Tom & Jerry.gba"))
        assertEquals(emptyList<String>(), ThumbNames.candidates(".nes"))
    }

    @Test fun urlEncodesSpacesAndBrackets() {
        assertEquals("https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Nintendo_Entertainment_System/master/Named_Boxarts/Super%20Mario%20Bros.%20%28World%29.png",
            ThumbNames.url(ThumbNames.base("Nintendo_-_Nintendo_Entertainment_System"), "Super Mario Bros. (World)"))
    }

    @Test fun initials() {
        assertEquals("SMB", ThumbNames.initials("Super Mario Bros."))
        assertEquals("RR", ThumbNames.initials("Robo Run"))
        assertEquals("QU", ThumbNames.initials("Quiz"))
        assertEquals("?", ThumbNames.initials("..."))
    }
}
