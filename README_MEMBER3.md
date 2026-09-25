# Member 3 – Data & Persistence Layer

> **Author:** Member 3 (Data & Persistence Engineer)
> **Branch:** `feature/data`

---

## Files Delivered

| File | Purpose |
|------|---------|
| `src/model/SequencerModel.java` | Core 8×16 grid model with save/load |
| `src/model/PatternFileException.java` | Custom unchecked exception for I/O errors |
| `src/model/PatternStorage.java` | Static `JFileChooser` helpers for toolbar wiring |
| `src/model/SequencerModelTest.java` | `main()`-based test suite (no JUnit) |
| `samples/arpeggio_cmajor.json` | Sample: ascending C-major arpeggio |
| `samples/basic_beat.json` | Sample: interlocking rhythm pattern |

---

## JSON File Format (version 1)

Pattern files are human-readable, pretty-printed JSON encoded in UTF-8:

```json
{
  "version": 1,
  "rows": 8,
  "cols": 16,
  "bpm": 120,
  "grid": [
    "1000100010001000",
    "0000000000000000",
    "0010001000100010",
    "0000000000000000",
    "0100010001000100",
    "0000000000000000",
    "0000000000000000",
    "1000000010000000"
  ]
}
```

### Field reference

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `version` | int | yes | Format version (currently `1`) |
| `rows` | int | yes | Must be `8` |
| `cols` | int | yes | Must be `16` |
| `bpm` | int | **optional** | Tempo, 40–240. If missing, the model's current BPM is retained. |
| `grid` | string[] | yes | Exactly 8 strings, each exactly 16 chars of `'0'` / `'1'`. Row 0 = C5 (top), row 7 = C4 (bottom). |

Unknown keys are silently ignored (forward-compatible).

### Row → frequency mapping

| Row | Note | Frequency (Hz) |
|-----|------|----------------|
| 0 | C5 | 523.25 |
| 1 | B4 | 493.88 |
| 2 | A4 | 440.00 |
| 3 | G4 | 392.00 |
| 4 | F4 | 349.23 |
| 5 | E4 | 329.63 |
| 6 | D4 | 293.66 |
| 7 | C4 | 261.63 |

---

## How Member 4 (UI Engineer) Calls This Code

### 1. Create the model (shared across the whole app)

```java
import model.SequencerModel;

SequencerModel model = new SequencerModel();
```

### 2. Read / write cells from GridPanel and AudioEngine

```java
// GridPanel mouse handler
model.toggleCell(row, col);
boolean on = model.isCellActive(row, col);

// AudioEngine playback loop
double freq = model.getFrequency(row);
```

All three methods are **synchronized**, so the Swing timer, mouse clicks,
and audio thread can safely share the same model instance.

### 3. Wire Save / Load toolbar buttons (one line each)

```java
import model.PatternStorage;

saveButton.addActionListener(e ->
    PatternStorage.saveWithDialog(frame, model));

loadButton.addActionListener(e ->
    PatternStorage.loadWithDialog(frame, model, () -> gridPanel.repaint()));
```

`PatternStorage` handles:
- `JFileChooser` with a `.json` filter
- Auto-appending `.json` if the user omits it
- Overwrite confirmation dialog
- Error dialogs for corrupt / missing files
- Remembering the last-used directory

### 4. Direct save / load (without dialogs)

```java
model.savePattern(new File("my_pattern.json"));
model.loadPattern(new File("my_pattern.json"));
```

Throws `PatternFileException` (unchecked) on any failure.

### 5. BPM (optional for Member 4)

```java
model.setBpm(140);       // range: 40–240
int bpm = model.getBpm(); // default: 120
```

BPM is persisted in the JSON file. If a loaded file has no `bpm` key,
the model's current BPM is kept unchanged.

### 6. Utility methods

```java
model.clear();                      // reset all cells to inactive
model.setCell(row, col, true);      // set a specific cell
```

---

## Building & Testing

```bash
# Compile
javac -d out src/model/*.java

# Run tests
java -cp out model.SequencerModelTest
```

All tests use `File.createTempFile` and clean up after themselves.

---

## Thread Safety

Every public method that touches `grid` or `bpm` is `synchronized` on
the model instance. This means:

- The Swing EDT (mouse clicks, repaint) can call `toggleCell` / `isCellActive`
- A `javax.swing.Timer` can advance the step cursor and read cells
- The audio engine can call `getFrequency` and `isCellActive`

…all without external locking, as long as they share the same `SequencerModel`
instance.
