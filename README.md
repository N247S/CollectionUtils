# CollectionUtils
A Map with advanced control over entry order.

<b>This map is not close to perfection.
If you have additions/suggestions/improvements,
please share them by posting them on this github!</b>

This Map acts like an regular {@link ArrayList} type, but with the difference
that it holds key and values instead of single entries. This Map is not about
the best performance, but about the best control over its (duplicated)
entries.<br>
<br>
Basically this Map a ListWrapper adapting to the Maps functionality. Meaning
it is not as efficient as other Map implementations might be. But it offers
advanced key/value control in return. Since Keys aren't the most important
object of the Map it is possible to replace keys just like values. Although
this means the behavior of this Map does differ the intended behavior of the
Map<br>
<br>
This Map supports {@link SubMap SubMaps} (including tailMaps and headMaps).
These SubMaps are backed up by the main ListMap, meaning they will be updated
once the ListMap changes and visa versa.<br>
<br>
Since the keys are not that important for identifying (because of the
indexing), the (key/value/entry)Views of this map have more operation support
as regular Views, Such as <tt>add</tt>, <tt>addAll</tt>, <tt>put</tt>,
<tt>putAll</tt>
Made by: N247S
