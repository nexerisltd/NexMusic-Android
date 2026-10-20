# Keep classes and constructors looked up from jni_bridge.cpp.
-keep class com.nexapp.nexmusic.usbaudio.model.DacFormat { <init>(...); }
-keep class com.nexapp.nexmusic.usbaudio.model.DacCapabilities { <init>(...); }

# Keep names used by the exported JNI method symbols.
-keepclasseswithmembernames class com.nexapp.nexmusic.usbaudio.UsbAudioDriver { native <methods>; }
