# Turn off tracking on Samsung, LG, Roku, VIDAA TVs

These TVs let no app do it for you: it takes the remote, once. Menu names change between years: if the words differ, look under **Privacy**.

In the phone or computer app: **More** › **Tools** › **Turn off tracking** opens Settings and shows these paths.

|  | Tracking | Ads | Mic |
| --- | --- | --- | --- |
| Samsung | Settings › All Settings › General & Privacy › Terms & Privacy › **Viewing Information Services**: Off. | Same place: turn off **Interest-Based Advertisements**, turn **Do not track** on. | Voice › Bixby › **Voice Wake-Up**: Off. |
| LG | All Settings › General › System › Additional Settings › **Live Plus**: Off. | User Agreements: untick **interest-based advertising** and **Who.Where.What?**; Additional Settings › Home Settings: turn off **Home Promotion** and **Content Recommendation**. | Turn off **Hands-free Voice** ("Hi LG"). |
| Roku | Home › Settings › Privacy › Smart TV Experience › **Automatic Content Recognition**: Off. No such menu: my.roku.com › Privacy Settings. Roku sticks have none. | Settings › Privacy › Advertising › **Personalize ads**: Off. | Settings › Privacy › Microphone: block per channel. |
| Hisense VIDAA | Settings › (System ›) Advanced Settings › Enhanced Viewing › **Automatic Content Recognition**: Off, then decline the pop-ups. | Same place: turn off **Personalized Ads** and **Content Recommendation**. | System › Voice Service › **Hands-Free**: Off. |

- **Content tracking** (ACR): the TV samples the picture and sound of whatever is on, HDMI too, to sell ads. Turning it off changes nothing you watch.
- Older menus: Samsung 2019–2021 under Settings › Support › Terms & Privacy; LG 2025 under Support › Privacy & Terms › User Agreements › **Viewing Information**.
- Block more on the network: set the TV's DNS to `94.140.14.14` (AdGuard DNS) in its network settings, or use Pi-hole / NextDNS with [HaGeZi](https://github.com/hagezi/dns-blocklists)'s lists for Samsung, LG and Roku.
- Don't decline the required terms: the TV would lose its apps.
- Android / Google TV: [Privacy check](privacy.md) and [Optimize TV](optimize.md) do it for you.

Menu paths from [cleanyour.tv](https://cleanyour.tv/not-android).
