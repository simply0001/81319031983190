param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

$rawDirectory = Join-Path $ProjectRoot "app\src\main\res\raw"
$fontDirectory = Join-Path $ProjectRoot "app\src\main\res\font"
$wrapperDirectory = Join-Path $ProjectRoot "gradle\wrapper"

New-Item -ItemType Directory -Force -Path $rawDirectory, $fontDirectory, $wrapperDirectory | Out-Null

$assets = [ordered]@{
    status_wifi = "https://www.figma.com/api/mcp/asset/68a786b9-8466-406e-8fc2-3d3c9ea055d5"
    status_battery = "https://www.figma.com/api/mcp/asset/7cb3f322-b20f-4e88-a11a-a4c4cb2151f3"
    nav_messages = "https://www.figma.com/api/mcp/asset/1fb84463-58e2-413b-bc6f-c6c13278256f"
    nav_friends = "https://www.figma.com/api/mcp/asset/be9f47d6-8936-4b67-81f5-df03ae67182d"
    nav_home = "https://www.figma.com/api/mcp/asset/c95ee179-12f3-4383-82b6-c6f48b34e6e3"
    nav_activities = "https://www.figma.com/api/mcp/asset/3d306328-e9bf-4637-bc95-f83b9138da35"
    nav_settings = "https://www.figma.com/api/mcp/asset/759606b0-dfd2-4010-8c37-d4d955667e63"
    home_avatar_matt = "https://www.figma.com/api/mcp/asset/824798fe-8be3-4094-846e-ee07d552f42a"
    online_dot = "https://www.figma.com/api/mcp/asset/e6a9e0ce-d42e-45c0-bc2a-81e267af162a"
    pattern_home_bottom = "https://www.figma.com/api/mcp/asset/3331e740-02b6-4ff1-8bf7-a3e606c65554"
    pattern_home_top = "https://www.figma.com/api/mcp/asset/cd26388a-70b6-4528-b025-f855b58567fd"
    home_avatar_petah = "https://www.figma.com/api/mcp/asset/0c31c5b7-6ecf-47cb-90f2-ba446a3e443d"
    filter_icon = "https://www.figma.com/api/mcp/asset/99767c3f-ddd5-42aa-893d-167f4d8d2bea"
    home_more = "https://www.figma.com/api/mcp/asset/04a0113b-5fd1-474e-821f-a6d9d5a7cb82"
    home_mood_happy = "https://www.figma.com/api/mcp/asset/996c6db9-1c47-47fa-9911-54ab34abd41c"
    home_mood_sad = "https://www.figma.com/api/mcp/asset/60b4ff8c-aad3-488e-a036-436bd697e88b"
    home_mood_neutral = "https://www.figma.com/api/mcp/asset/612e525b-eda9-4db9-9d7e-ea4ba91bd13c"
    home_mood_party = "https://www.figma.com/api/mcp/asset/75e40f36-32a3-4dab-9a18-690a3411aacd"
    home_mood_playful = "https://www.figma.com/api/mcp/asset/d26ad060-b882-4e96-8158-3c21e669a760"
    home_mood_cool = "https://www.figma.com/api/mcp/asset/aaeb6620-c4ab-4c53-9446-2848f1fed040"
    home_emoji_cluster = "https://www.figma.com/api/mcp/asset/97900103-bdcc-4ee9-b484-01dc9ac09fc7"
    pattern_activities_bottom = "https://www.figma.com/api/mcp/asset/ea920f3b-2825-41a6-9ba4-97129869c6a7"
    pattern_activities_top = "https://www.figma.com/api/mcp/asset/225f36d6-43bd-485b-8849-5c83b1f0ce1f"
    activities_games = "https://www.figma.com/api/mcp/asset/d5105cf8-5894-49ff-b3fd-6c1be56715bd"
    activities_arrow_green = "https://www.figma.com/api/mcp/asset/6f62bd3f-e33a-4a39-b6d5-881032b4069d"
    activities_shop = "https://www.figma.com/api/mcp/asset/2abd56aa-1db0-4172-b8b6-ae180c846dbd"
    activities_arrow_orange = "https://www.figma.com/api/mcp/asset/8087a4f1-3f01-4a1a-9455-48965e9c567f"
    activities_trophy = "https://www.figma.com/api/mcp/asset/2dfad04f-0155-4f5e-8844-508d6199279d"
    activities_arrow_yellow = "https://www.figma.com/api/mcp/asset/26afe4aa-bd72-460d-bbec-839365526407"
    activities_coin_default = "https://www.figma.com/api/mcp/asset/537b0502-d822-4651-aa9d-03b4e1ecdbd7"
    activities_puzzle_default = "https://www.figma.com/api/mcp/asset/5e87427b-6d06-427c-8eef-a66111b85766"
    activities_shuffle = "https://www.figma.com/api/mcp/asset/b7f0726c-9203-44dc-8acd-0c8107e25dc0"
    activities_coin_alt = "https://www.figma.com/api/mcp/asset/acc2ab5b-cf50-43a2-86fd-cffd6a649feb"
    activities_puzzle_alt = "https://www.figma.com/api/mcp/asset/51e5fd83-bf49-4071-be0d-3e15f6fe0e7d"
    pattern_messages_top = "https://www.figma.com/api/mcp/asset/e5c40df2-4792-4ed5-8c68-4da89c40c854"
    pattern_messages_bottom = "https://www.figma.com/api/mcp/asset/5f5420e4-52bd-4d6a-825e-0a9624d0afae"
    messages_avatar_spob = "https://www.figma.com/api/mcp/asset/b532ba38-feec-47df-a8ff-d370719c7b5e"
    messages_avatar_sans = "https://www.figma.com/api/mcp/asset/26a43f68-e2b3-4e48-9d72-2a313a91a2ae"
    messages_badge = "https://www.figma.com/api/mcp/asset/fada6442-4d80-4b20-bbd2-7a3894955369"
    pattern_friends_bottom = "https://www.figma.com/api/mcp/asset/9c960923-32cd-43e4-85dc-cf24d402cc2c"
    friends_avatar_matt = "https://www.figma.com/api/mcp/asset/e4bea201-59ac-49e8-be67-5f6879fea28a"
    pattern_friends_top = "https://www.figma.com/api/mcp/asset/b14a3704-e753-45b4-82e5-284fd2f0e513"
    friends_filter = "https://www.figma.com/api/mcp/asset/cbc4837e-bccc-45c0-87e3-70b79c107d78"
    friends_badge = "https://www.figma.com/api/mcp/asset/7297e643-b36d-4bb6-9d26-0ce9b4aeb7e0"
    pattern_settings_bottom = "https://www.figma.com/api/mcp/asset/a05e7bd8-62c3-43c5-b411-9e28e98c1886"
    pattern_settings_top = "https://www.figma.com/api/mcp/asset/3471b381-189b-41f5-a296-c8dbceb0ad59"
    settings_nearby = "https://www.figma.com/api/mcp/asset/2612855b-3e4e-4023-85f4-34e70e198d78"
    settings_switch_on = "https://www.figma.com/api/mcp/asset/347257b2-93cf-4b37-8572-70153d98e9f0"
    settings_sound = "https://www.figma.com/api/mcp/asset/f5d59f03-340c-4998-9a2a-0d68fb276e7e"
    settings_gear = "https://www.figma.com/api/mcp/asset/0c2701e0-b024-40a4-8c8d-6334698d28b0"
    settings_notifications = "https://www.figma.com/api/mcp/asset/1bcbc3d4-d5d0-4b57-affe-383ab2a296a5"
    settings_arrow = "https://www.figma.com/api/mcp/asset/cefd5da7-6e58-40cc-93ad-472fb22fb118"
    settings_theme = "https://www.figma.com/api/mcp/asset/89f45e4a-3161-4e1e-9652-9c7c51a6fb1a"
    theme_light = "https://www.figma.com/api/mcp/asset/14078219-c227-4b0b-8695-22e94ad544a8"
    theme_system = "https://www.figma.com/api/mcp/asset/e11268b5-998e-4167-a883-110ebe252acc"
    theme_dark = "https://www.figma.com/api/mcp/asset/a5d11ce3-f4dc-4b35-a84f-39cc4d8ad677"
    settings_version = "https://www.figma.com/api/mcp/asset/42166711-fbda-46f7-b760-def8ee394878"
    settings_credits_avatar = "https://www.figma.com/api/mcp/asset/6f13e573-85b5-4a18-b784-af5fbc9f813c"
    settings_delete = "https://www.figma.com/api/mcp/asset/2ff2e6b5-a63f-499e-91a1-570597445914"
}

foreach ($asset in $assets.GetEnumerator()) {
    $destination = Join-Path $rawDirectory ($asset.Key + ".asset")
    if (-not (Test-Path -LiteralPath $destination)) {
        & curl.exe -L --fail --silent --show-error -o $destination $asset.Value
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to download Figma asset '$($asset.Key)'"
        }
    }
}

$binaryDownloads = [ordered]@{
    (Join-Path $fontDirectory "rubik_variable.ttf") = "https://raw.githubusercontent.com/google/fonts/main/ofl/rubik/Rubik%5Bwght%5D.ttf"
    (Join-Path $fontDirectory "instrument_sans_variable.ttf") = "https://raw.githubusercontent.com/google/fonts/main/ofl/instrumentsans/InstrumentSans%5Bwdth,wght%5D.ttf"
    (Join-Path $wrapperDirectory "gradle-wrapper.jar") = "https://raw.githubusercontent.com/gradle/gradle/v9.5.0/gradle/wrapper/gradle-wrapper.jar"
}

foreach ($download in $binaryDownloads.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $download.Key)) {
        & curl.exe -L --fail --silent --show-error -o $download.Key $download.Value
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to download '$($download.Key)'"
        }
    }
}

Write-Output "Downloaded $($assets.Count) Figma assets, two bundled fonts, and the Gradle wrapper."
