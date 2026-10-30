package dev.tvremote.channels

/**
 * The channels the player steps through with ▲▼ / CH+ CH- / numbers: the list the channel was chosen from (a country, one of
 * its categories, the favourites), or for a link the channel's whole country. Kept here between the two screens.
 */
object Lineup {
    @Volatile var channels: List<List<Channel>> = emptyList()
        private set
    @Volatile var title: String = ""
        private set

    fun set(list: List<List<Channel>>, name: String) { channels = list; title = name }

    fun indexOf(id: String) = channels.indexOfFirst { it.first().id == id }
}
