param([string]$JavaHome,[string]$TestDir)
$ErrorActionPreference='Stop'
if(!$JavaHome){throw 'Pass -JavaHome pointing to JDK 17'}
if(!$TestDir){$TestDir=Join-Path $PSScriptRoot 'test-build'}
New-Item -ItemType Directory -Force $TestDir | Out-Null
$p="$PSScriptRoot/app/src/main/java/dev/touchxbox/pad"
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -d $TestDir "$p/PadState.java" "$p/HidDescriptor.java" "$p/BridgeAuth.java" "$p/LayoutProfile.java" "$p/LayoutViewport.java" "$p/StickGesture.java" "$p/ButtonChord.java" "$p/ButtonPress.java" "$p/KeyboardKeys.java" "$p/ControllerProtocol.java" "$p/MotionCodec.java" "$p/SwitchProCodec.java" "$p/Ds4Codec.java" "$p/FpvCodec.java" "$p/FpvMode.java" "$p/TemplateOrder.java" "$p/BridgeRetry.java" "$PSScriptRoot/tests/ProtocolTest.java" "$PSScriptRoot/tests/AuthTest.java" "$PSScriptRoot/tests/LayoutTest.java" "$PSScriptRoot/tests/LayoutViewportTest.java" "$PSScriptRoot/tests/ResponsiveLayoutTest.java" "$PSScriptRoot/tests/MotionReportTest.java" "$PSScriptRoot/tests/ShoulderInteractionTest.java" "$PSScriptRoot/tests/FpvStickTest.java" "$PSScriptRoot/tests/TemplateOrderTest.java" "$PSScriptRoot/tests/BridgeRetryTest.java" "$PSScriptRoot/tests/FpvModeTest.java"
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
foreach($name in @('ProtocolTest','AuthTest','LayoutTest','LayoutViewportTest','ResponsiveLayoutTest','MotionReportTest','ShoulderInteractionTest','FpvStickTest','TemplateOrderTest','FpvModeTest','dev.touchxbox.pad.BridgeRetryTest')){& "$JavaHome/bin/java.exe" -cp $TestDir $name;if($LASTEXITCODE -ne 0){throw "Failed: $name"}}
