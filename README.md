# Ouvido Absoluto AI — Android MVP 0.5

Primeira base executável do objetivo principal do app: ouvir uma pessoa cantando pelo microfone e estimar a tonalidade mais provável localmente.

## Incluído
- Kotlin + Jetpack Compose
- Captura de microfone com AudioRecord
- Pitch vocal por YIN simplificado
- Conversão frequência -> nota/cents
- Perfil cromático ponderado pelo tempo/confiança
- Comparação das 24 tonalidades (12 maiores + 12 menores)
- Confiança limitada pela duração da amostra
- Histórico local com Room
- Nenhuma API paga ou conexão obrigatória

## Abrir
1. Instale Android Studio Quail 4 ou mais recente.
2. Abra esta pasta como projeto.
3. Instale Android SDK 37 quando solicitado.
4. Aguarde o Gradle Sync.
5. Execute em um aparelho Android real; microfone em emulador pode não representar bem a voz.

## Build
No Android Studio: Build > Build APK(s).

Ou em terminal, quando o Gradle Wrapper estiver disponível:
`./gradlew assembleDebug`

## Observação
O detector tonal desta versão é um MVP técnico. A arquitetura já está preparada para as próximas melhorias: eventos de nota, finais de frase, relativo maior/menor, estabilidade temporal e calibração de afinação.

## MVP 0.2 — Implementação 13

- YIN com interpolação parabólica para frequência mais precisa.
- Filtro de mediana vocal e correção conservadora de erro de oitava.
- Eventos de nota com duração mínima, reduzindo transições e vibrato no detector tonal.
- Evidências adicionais de tônica, quinta, terça e finais de frase.
- Estabilidade temporal antes de marcar a tonalidade como estabilizada.
- Exibição de alternativa tonal e quantidade de eventos/frases.
- Testes unitários básicos para A4 e perfil de Dó maior.

## MVP 0.3 — Implementação 14

- Analisador dedicado a maior × menor relativo com penalização de confiança quando os pares ficam próximos.
- Evidência melódica adicional por resolução de sensível/supertônica para a tônica e retornos ao centro tonal.
- Resultado não é marcado como estabilizado quando o modo relativo continua ambíguo.
- Exibição automática da relativa, escala e tríade principal do tom detectado.
- Reprodução local, sem internet, da tônica, acorde principal e escala por PCM/AudioTrack.
- Interface orienta o usuário a continuar cantando quando o par relativo ainda não estiver resolvido.

## Build sem PC — GitHub Actions

A versão 0.4 inclui `.github/workflows/build-apk.yml`, que permite gerar o APK usando somente o GitHub pelo celular. Consulte `GUIA-CELULAR.md`.

## MVP 0.5 — Precisão e qualidade da amostra

- Troca de nota confirmada por três quadros consecutivos, reduzindo falsos eventos causados por vibrato e ruído.
- Resultado estável exige tempo mínimo, cinco eventos e pelo menos quatro notas diferentes.
- Confiança limitada quando a amostra é curta ou possui pouca variedade melódica.
- Orientações ao vivo informam se é preciso continuar cantando ou variar mais as notas.
- Testes automatizados garantem que uma única nota repetida não seja apresentada como tonalidade confiável.
