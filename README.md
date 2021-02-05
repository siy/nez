Nez : open grammar and tools
===================

Nez is an open grammar specification language. 
Once you write a grammar for complex data or computer languages, 
you can use it anywhere for various purposes including pattern matchers, 
transformers, interpreters, compilers and other language tools.

For furthermore information, you will visit [here](http://nez-peg.github.io/).

Build Latest Software Distribution
-----------

To build the latest nez.jar file:

```
git clone git@github.com:nez-peg/nez.git
cd nez
ant
```

Now, you will run `nez.jar` with:

```
java -jar nez.jar ....
```

On Linux systems you can use `nez.sh` as follows:
- copy `nez.sh` into user binaries directory, usually something like `~/.local/bin`
- copy built `nez.jar` into `lib` directory next to user binaries directory. For example, if user binaries are in  `~/.local/bin`, then `nez.jar` should be put into `~/.local/lib`.

Now you can invoke NEZ just like any other tool:
```
 $ nez ...
```
Without parameerts utility will print usage information, available commands, etc.

## Development
Nez is originally developed by [Kimio Kuramitsu](http://kuramitsulab.github.io/) with his graduate students in Yokohama National University, JAPAN. 

You are welcome to contribute code. 
You can send code both as a patch or a GitHub pull request.

Note that Nez is still very much work in progress. 
There are no compatibility guarantees while the _beta_ version.


