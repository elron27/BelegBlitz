# Regeln für R8 (Verkleinern der Release-Version).
# Room, ML Kit und Compose bringen ihre eigenen Regeln mit.

# Zeilennummern in Absturzberichten behalten
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit sucht seine Bausteine (ComponentRegistrar) per Reflexion und erzeugt sie mit dem parameterlosen
# Konstruktor. R8 im Vollmodus entfernt diesen sonst (NullPointerException in FaceDetection/TextRecognition).
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
    public java.util.List getComponents(); 
}