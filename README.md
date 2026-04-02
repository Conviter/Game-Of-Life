# Game of Life #

Dies ist eine Implementation des Game of Life von John Conway. 

Es gibt einige Kontrolelemente, so kann das Zoom level angepasst werden, die tick rate der Simulation geändert werden, oder Zellen erzeugt oder gelöscht werden. Auch kann die Kamera mit rechter Maustaste bewegt werden.

Dieses Projekt ist ein WIP, ich arbeite immer noch an weiteren Optimierungsansetzen. Aus diesem Grund gibt es unterschiedliche Varianten.

Zu diesem Zeitpunkt ist die Variante ParallelGridBitGrid die schnellste, und die an welcher ich weiter arbeite. Alle anderen sind nur zum dokumentieren, welche ansätze ich getestet habe und als nicht effizient empfunden habe.

In  diesem Ansatz wird das Universum in Chunks unterteilt, welche parallel durch multithreading bearbeitet werden. Dabei werden nur Chunks mit Zellen berücksichtigt, so das bei einem extremst dünn belebten Universum die Laufzeit trotzdem noch gut bleibt. Innerhalb eines Chunks wird der status der zellen als bits eines long arrays kodiert. So das ein 512 x 512 zellen chunk in einem long array der größe 4096 gespeichert wird. Dadurch ist die verarbeitung dieser chunks deutlich schneller als  es mit einem reinen sparsen ansatz währe. Zuätzlich ermöglicht diese Struktur das anwenden von vectorrechnungen, um eine zeile zellen auf einmal zu bearbeiten, was die laufzeit nochmal um ein vielfaches verbessern würde. Dies ist aktuell jedoch noch nicht umgesetzt.

Aktuell kann dieses Programm ca. 50 Millionen Zellen pro sekunde auf meiner Intel i7 13700k simulieren. 

Ein Video das die Simulation zeigt, jedoch eine alte version:

https://github.com/user-attachments/assets/7ccce8a3-480f-410a-a572-bd7cb997e89a

