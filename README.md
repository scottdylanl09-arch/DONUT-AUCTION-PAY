# DonutSMP Profit Tracker

A client-side Fabric mod for Minecraft **26.3** that adds up the money you receive and send with `/pay` on
DonutSMP and shows it on your HUD:

```
DonutSMP Profit
Gained      +$12.5M      <- green
Lost         -$3.2M      <- red
Up           +$9.3M      <- green when you are up
Last: +$2.5M from Steve
```

If you are **down**, the last line becomes `Down  -$4.1M` in **red**.

It only reads chat lines the server already sends you and draws a HUD. It never sends anything to the server.
Only player-to-player payments are counted (not shop, auction house or spawner money).

## What it recognises

| Chat line | Counted as |
| --- | --- |
| `Steve paid you $2.5K` | gained |
| `You were paid $ 60K by Steve` | gained |
| `You paid Steve $5M` | lost |

Amounts like `$1,500`, `$250.50`, `$2.5K`, `$ 25M`, `$1.5B` and `$2T` all work, and so do Bedrock names that
start with a dot (`.Name`).

## 1. Build the jar (pick one)

The source has to be compiled once to get the `.jar` file you put in your mods folder.

### A) With GitHub (nothing to install)

1. Unzip this download. Make a free account at github.com, click **New repository**, create it, then click
   **uploading an existing file**.
2. Drag everything that is *inside* the unzipped `donutprofit` folder into the page and click **Commit changes**.
   Make sure the `.github` folder goes up too (it is hidden on a Mac: press Cmd+Shift+. in Finder).
   If the Actions tab shows no workflow afterwards, use **Add file > Create new file**, name it
   `.github/workflows/build.yml` and paste in the contents of that file.
3. Open the **Actions** tab. After a few minutes "Build mod jar" gets a green tick. Click it, scroll down to
   **Artifacts**, download `donutprofit-jar`, and unzip it. Inside is `donutprofit-1.0.0.jar`.

### B) On your own PC

Install JDK 25 and Gradle 9.6 or newer, open a terminal in this folder and run:

```
gradle build
```

The jar is created in `build/libs/donutprofit-1.0.0.jar`. The first build downloads Minecraft and takes a while.

## 2. Install

1. Install Fabric Loader for Minecraft 26.3 (fabricmc.net/use/installer).
2. Download **Fabric API** for 26.3 (Modrinth or CurseForge).
3. Put the Fabric API jar and `donutprofit-1.0.0.jar` in your `.minecraft/mods` folder and launch the Fabric profile.

## 3. Use it

| Command | What it does |
| --- | --- |
| `/donutprofit` | Prints your totals in chat |
| `/donutprofit reset` | Sets gained and lost back to $0 |
| `/donutprofit hud` | Shows or hides the HUD |
| `/donutprofit pos <x> <y>` | Moves the HUD (pixels from the top-left corner) |
| `/donutprofit lines` | Lists recent chat lines that mention `$` and "pay" but were **not** counted |
| `/donutprofit test <chat line>` | Checks whether a line would be counted (adds nothing to your totals) |

Your totals are saved between sessions in `config/donutprofit-stats.json`.
Settings are in `config/donutprofit.json` (HUD position, `abbreviateAmounts`, `resetOnLaunch`, ...).
Edit that file while the game is closed.

## If a payment is not counted

The message wording was taken from other DonutSMP mods and real chat logs, but the server can change it.

1. Make a small `/pay`, then run `/donutprofit lines`. If the payment shows up there, its wording differs from
   the built-in patterns.
2. Open `config/donutprofit.json` and add or change a pattern in `receivedPatterns` or `sentPatterns`.
   A pattern must match the whole line and contain the groups `(?<name>...)` and `(?<amount>...)`.
   Backslashes are doubled in the file (`\\d`, not `\d`). Restart the game after editing.
3. Use `/donutprofit test <the line>` to try it.

Patterns have to match the whole line on purpose: another player typing "Steve paid you $1M" in public chat
shows up with their name in front, so it is not counted.

## Other Minecraft versions

The versions are in `gradle.properties`. For another 26.x release copy the current `minecraft_version`,
`loader_version`, `loom_version` and `fabric_api_version` from fabricmc.net/develop (and the Gradle version in
`.github/workflows/build.yml`). This code is written for the 26.x unobfuscated game; Minecraft 1.21.x needs a
different build.
