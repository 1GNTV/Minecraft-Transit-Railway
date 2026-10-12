# Expérience Oculus 1.8.0 — injection de source GLSL sans Args$1

**Branche séparée du build CPU/70 FPS. Ne pas considérer comme version stable.**

Ancienne injection `OculusInstanceShaderMixin` :
`@ModifyArgs` sur le constructeur d'IrisProgramResourceFactory.
Ce Mixin produisait un `NoClassDefFoundError` pour la classe
synthétique `org.spongepowered.asm.synthetic.args.Args$1`.

Nouvel essai : `OculusShaderMapMixin` utilise `@ModifyVariable`
sur la première carte `Map<PatchShaderType,String>` stockée par
`ShaderCreator.create`. Il ne fait jamais référence aux types Oculus
à la compilation MTR, et sa méthode de transformation est
**désactivée par défaut**.

- Sans `-Dmtr.experimental.oculusMapBridge=true` : la même
  `Map` est renvoyée inchangée, sans essai de patch.
- Avec ce flag : les chaînes de vertex et fragment shader sont
  transmises **ensemble** au patcher existant qui compile/prévalide
  la paire. Les programmes avec géométrie/tessellation ou non pris
  en charge gardent leurs sources originales.
- `require=0` : si la version d'Oculus ne possède pas le local ciblé,
  aucun changement n'est imposé.
- En cas d'erreur de prévalidation, aucune transformation n'est
  rendue. Les mutations de sources sont appliquées dans une nouvelle
  `HashMap`, sans toucher à la carte d'origine.

La branche réutilise les optimisations CPU `queue-hotpath-v2`
et ses tests.

## Test en jeu impératif

1. Installer d'abord le build **CPU** stable de Queue Hotpath V2
   sans aucun flag expérimental ; vérifier qu'il retrouve une
   performance acceptable avec Complementary.
2. Installer ce JAR expérimental **sans flag** : il doit arriver
   jusqu'au menu et reproduire le comportement de base.
3. Activer **seulement ensuite**
   `-Dmtr.experimental.oculusMapBridge=true`.
4. Redémarrer Minecraft et vérifier sans équivoque qu'il atteint
   le menu. Vérifier la texture, la profondeur, les silhouettes
   et les ombres des trains ; essayer un wagon puis plusieurs
   modèles identiques.
5. Comparer FPS, 1% lows, profil Spark et logs
   `MTR Next instances`. Un effet nul signifie que le shader
   pack ou Oculus n'est pas éligible : ne pas revendiquer
   l'instancing comme opérationnel.

**Les tests CI compilent les shaders OpenGL de comparaison et
vérifient le JAR, mais ils ne peuvent pas simuler Oculus sous
le modpack Minecraft réel. L'intégration expérimentale peut
encore être inopérante ou provoquer un crash ; conserver les deux
JARs distincts et ne jamais installer les deux simultanément.**
