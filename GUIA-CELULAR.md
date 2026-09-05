# Ouvido Absoluto AI — gerar o APK usando apenas o celular

Esta versão já contém um workflow do GitHub Actions que compila o aplicativo na nuvem. Você não precisa de computador nem Android Studio.

## 1. Criar uma conta no GitHub

Abra https://github.com no navegador do celular e crie uma conta gratuita, se ainda não tiver uma.

## 2. Criar um repositório

1. No GitHub, toque em **+** e depois em **New repository**.
2. Nome sugerido: `OuvidoAbsolutoAI`.
3. Pode deixar como **Public** para simplificar e não consumir minutos privados desnecessariamente.
4. Toque em **Create repository**.

## 3. Enviar os arquivos

O GitHub pelo navegador não importa uma pasta ZIP diretamente como projeto completo. Extraia este ZIP no celular e envie o conteúdo da pasta `OuvidoAbsolutoAI` para a raiz do repositório.

Arquivos importantes que precisam aparecer na raiz:

- `.github/workflows/build-apk.yml`
- `app/`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`

Se o gerenciador de arquivos esconder a pasta `.github`, ative a opção para mostrar arquivos ocultos.

## 4. Gerar o APK

1. Abra o repositório no GitHub.
2. Entre na aba **Actions**.
3. Abra **Gerar APK Android**.
4. Toque em **Run workflow**.
5. Escolha a branch `main` e confirme em **Run workflow**.

O workflow executa testes e depois gera o APK de debug.

## 5. Baixar no celular

Quando a execução estiver com o símbolo verde:

1. Abra a execução concluída.
2. Vá até **Artifacts** no final da página.
3. Toque em **OuvidoAbsolutoAI-debug**.
4. O GitHub baixa um ZIP do artefato.
5. Extraia esse ZIP.
6. Dentro estará `OuvidoAbsolutoAI-0.4-debug.apk`.
7. Toque no APK para instalar.

Na primeira instalação, o Android poderá pedir autorização para instalar aplicativos de fonte desconhecida para o navegador ou gerenciador de arquivos usado. Autorize somente para essa instalação se desejar.

## 6. Primeira execução

Ao abrir o Ouvido Absoluto AI, permita o acesso ao microfone. O áudio é processado localmente no aparelho; o MVP não precisa enviar sua voz para um servidor.

## Se o build ficar vermelho

Abra a execução em **Actions**, toque na etapa que falhou e copie a mensagem de erro. Envie essa mensagem ao ChatGPT para que o projeto possa ser corrigido.
