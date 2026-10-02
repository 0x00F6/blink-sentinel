# Third-party notices

Blink Sentinel is an independent project. Amazon and Blink names identify the systems it interoperates with; no official affiliation or endorsement is claimed.

## blinkpy

The Blink mobile protocol adapter was informed by the OAuth, PKCE, 2FA and API behavior documented in [blinkpy](https://github.com/fronzbot/blinkpy), version 0.25.9. This project does not bundle Python or run blinkpy. The following upstream notice is retained for protocol adaptation and attribution:

```text
MIT License

Copyright (c) 2017 Kevin Fronczak

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Build and runtime dependencies

The project resolves AndroidX / Compose / DataStore / Lifecycle (Apache-2.0), Kotlin and kotlinx.coroutines (Apache-2.0), OkHttp (Apache-2.0), and Gradle (Apache-2.0). Test dependencies include JUnit (EPL-1.0), MockWebServer (Apache-2.0), and the JVM org.json test adapter (its upstream JSON license). Review the upstream dependency metadata for complete notices when redistributing modified binaries.

## Artwork

The original Blink Sentinel artwork was generated for this project. It does not incorporate an Amazon or Blink logo. See `artwork/README.md`.
