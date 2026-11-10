# aigifmaker-clj

Turn text into animated GIFs with AI GIF Maker. Native GIF generation — no video conversion. Type what you want and download your GIF in about a minute.

This is the official Clojure SDK for [AI GIF Maker](https://aigifmaker.ai/?utm_source=clojars&utm_medium=package). One call sends your prompt, waits for the GIF and returns its URL.

```clojure
(require '[aigifmaker.core :as gif])

(gif/generate "a corgi in sunglasses dancing on a beach, looping")
;; => URL of the finished GIF
```

## Install

deps.edn:

```clojure
io.github.hanshs474/aigifmaker-clj {:mvn/version "0.1.0"}
```

Leiningen:

```clojure
[io.github.hanshs474/aigifmaker-clj "0.1.0"]
```

A thin wrapper over the zero-dependency Java client `io.github.hanshs474/aigifmaker` on Maven Central.

## API key

Create a key at https://aigifmaker.ai/settings/apikeys, then pass it as `:api-key` or export it:

```bash
export AIGIFMAKER_API_KEY=sk-...
```

Each GIF costs 1 credit on your AI GIF Maker account, the same as on the website. New accounts get one free GIF (it carries a small aigifmaker.ai badge). A GIF that fails to generate gives its credit back. Credit packs are on the [pricing](https://aigifmaker.ai/pricing?utm_source=clojars&utm_medium=package) page.

```clojure
(gif/credits)   ;; remaining credits, free to call
```

## Submit now, collect later

```clojure
(def id (gif/submit "happy birthday text bouncing in rainbow letters"))
(gif/status id)   ;; => {:id ... :status "pending" | "processing" | "success" | "failed" :url ... :queued ...}
```

A run from an account that has not bought credits yet may wait in a short queue first (`:queued true` while `:status` stays `"processing"`). That is normal; `generate` keeps polling every 3 seconds, for up to 15 minutes by default.

Options map: `:api-key`, `:base-url`, `:timeout-ms`, `:poll-ms`, or `:client` (a configured Java client).

## Errors worth catching

Failures throw `ex-info` whose data carries `:reason`:

- `:auth`: no key, or the key is wrong or deleted
- `:quota`: the account is out of credits
- `:rejected`: the content filter refused the prompt, or a cooldown is active; reword it
- `:failed`: the run was accepted but failed; the message says why
- `:timeout`: the wait ran out; the GIF may still finish in your history

Anything else is `:other` with the server's message.

## Tests

```bash
java -cp <clojure, spec.alpha, core.specs.alpha and aigifmaker jars>:src:test clojure.main -m aigifmaker.core-test
AIGIFMAKER_SMOKE=1 ...   # plus two free live checks, no generation
```

MIT licensed. Questions: support@aigifmaker.ai
