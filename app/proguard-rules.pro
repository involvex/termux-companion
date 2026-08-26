# Default ProGuard rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

# Room entities/DAOs
-keep class com.termux.companion.data.db.** { *; }

# Gson reflection targets (AI chat request models)
-keep class com.termux.companion.data.ai.AISuggestionService$ChatRequest { *; }
-keep class com.termux.companion.data.ai.AISuggestionService$ChatMessage { *; }

# Gson generic type tokens
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
