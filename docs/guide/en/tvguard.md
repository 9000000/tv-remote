# TV Setup repairs DNS and ADB by itself

TV Setup watches two things that often cut the TV off, and fixes them itself.

| What | TV Setup does |
| --- | --- |
| A DNS filter (the **🛡️ DNS filter** card) stops working: YouTube says it cannot connect although the TV is online | Checks now and then and when the network changes. If names fail while addresses answer, it **turns the filter off** and tells you on the Manage page. |
| The TV switches **ADB debugging** off | Switches ADB back on (it was granted the right once through ADB). |

> 💡 You need to do nothing. To use a DNS filter again, switch it on in the Setup tab (needs Android 9 or newer).
