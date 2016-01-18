# CollectionUtils
A Map with advanced control over entry order.

<b>This map is not close to perfection.
If you have additions/suggestions/improvements,
please share them by posting them on this github!</b>

This Map acts like an regular ArrayList type, but with the difference that it holds key and values instead of single entries.
This Map is not about the best performance, but about the best control over its entries.<br>
<br>
The ListMap contains a collection of ListMapEntries which are held in the same order they were put in.
(its behavior is similar to an ArrayList). This Map accept null keys and values, though it doesn't accept null entries.<br>
<br>
This ListMap supports SubMaps (including tailMaps and headMaps).
These SubMaps are backed up by the main ListMap, meaning they will be updated once the ListMap changes and visa versa.<br>
<br>
This map is far from efficient, so if someone is able to accomplish the same effect with a ArrayList
of Pairs. Its way better to do it as such, since there is no advanced search algorithms involved.
What this Map does offer in return is advanced key/value control.
Since Keys aren't the most important object of the Map it is possible to replace keys just like values.<br>
<br>
Since the keys are equally important as values, the {@link Set SetViews} of this map have more operation support as regular SetViews,
Such as <tt>add</tt>, <tt>addAll</tt>, <tt>put</tt>, <tt>putAll</tt><br>
<br>
Made by: N247S
