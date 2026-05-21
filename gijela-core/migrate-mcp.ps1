$srcRoot = "d:\workspace\gijela\gijela-core\gijela-core-chat\src"
$dstRoot = "d:\workspace\gijela\gijela-core\gijela-core-llm\gijela-core-llm-sdk-mcp\src"
$mainSrc = "$srcRoot\main\java\com\gijela\morpheus\chat\adapter\mcp"
$mainDst = "$dstRoot\main\java\com\gijela\morpheus\llm\sdk\mcp"
$testSrc = "$srcRoot\test\java\com\gijela\morpheus\chat\adapter\mcp"
$testDst = "$dstRoot\test\java\com\gijela\morpheus\llm\sdk\mcp"

function ConvertFile($src,$dst) {
  $c = Get-Content -Raw $src
  $c = $c -replace 'package com\.gijela\.morpheus\.chat\.adapter\.mcp\.client\.transport;','package com.gijela.morpheus.llm.sdk.mcp.client.transport;'
  $c = $c -replace 'package com\.gijela\.morpheus\.chat\.adapter\.mcp\.client;','package com.gijela.morpheus.llm.sdk.mcp.client;'
  $c = $c -replace 'package com\.gijela\.morpheus\.chat\.adapter\.mcp\.skill;','package com.gijela.morpheus.llm.sdk.mcp.skill;'
  $c = $c -replace 'com\.gijela\.morpheus\.chat\.adapter\.mcp\.client\.transport\.','com.gijela.morpheus.llm.sdk.mcp.client.transport.'
  $c = $c -replace 'com\.gijela\.morpheus\.chat\.adapter\.mcp\.client\.','com.gijela.morpheus.llm.sdk.mcp.client.'
  $c = $c -replace 'com\.gijela\.morpheus\.chat\.adapter\.mcp\.skill\.','com.gijela.morpheus.llm.sdk.mcp.skill.'
  $c = $c -replace 'com\.gijela\.morpheus\.chat\.config\.McpSdkProperties','com.gijela.morpheus.llm.sdk.mcp.McpSdkProperties'
  Set-Content -Path $dst -Value $c -NoNewline -Encoding UTF8
  Write-Host ("  copied: " + (Split-Path -Leaf $src))
}

Get-ChildItem "$mainSrc\client" -File -Filter *.java | ForEach-Object { ConvertFile $_.FullName "$mainDst\client\$($_.Name)" }
Get-ChildItem "$mainSrc\client\transport" -File -Filter *.java | ForEach-Object { ConvertFile $_.FullName "$mainDst\client\transport\$($_.Name)" }
Get-ChildItem "$mainSrc\skill" -File -Filter *.java | ForEach-Object { ConvertFile $_.FullName "$mainDst\skill\$($_.Name)" }
ConvertFile "$testSrc\client\McpJsonRpcClientTest.java" "$testDst\client\McpJsonRpcClientTest.java"
ConvertFile "$testSrc\client\transport\StdioTransportTest.java" "$testDst\client\transport\StdioTransportTest.java"
ConvertFile "$testSrc\skill\McpToolSkillProviderTest.java" "$testDst\skill\McpToolSkillProviderTest.java"
ConvertFile "$testSrc\skill\McpSkillSyncTest.java" "$testDst\skill\McpSkillSyncTest.java"
Write-Host "DONE"
