package nez.parser.vm;

public class MozSet {
	public static final byte Nop = 0; // Do nothing
	public static final byte Fail = 1; // Fail
	public static final byte Alt = 2; // Alt
	public static final byte Succ = 3; // Succ
	public static final byte Jump = 4; // Jump
	public static final byte Call = 5; // Call
	public static final byte Ret = 6; // Ret
	public static final byte Pos = 7; // Pos
	public static final byte Back = 8; // Back
	public static final byte Skip = 9; // Skip

	public static final byte Byte = 10; // match a byte character
	public static final byte Any = 11; // match any
	public static final byte Str = 12; // match string
	public static final byte Set = 13; // match set
	public static final byte NByte = 14; //
	public static final byte NAny = 15; //
	public static final byte NStr = 16; //
	public static final byte NSet = 17; //
	public static final byte OByte = 18; //
	public static final byte OAny = 19; //
	public static final byte OStr = 20; //
	public static final byte OSet = 21; //
	public static final byte RByte = 22; //
	public static final byte RAny = 23; //
	public static final byte RStr = 24; //
	public static final byte RSet = 25; //

	public static final byte Consume = 26; //
	public static final byte First = 27; //

	public static final byte Lookup = 28; // match a character
	public static final byte Memo = 29; // match a character
	public static final byte MemoFail = 30; // match a character

	public static final byte TPush = 31;
	public static final byte TPop = 32;
	public static final byte TLeftFold = 33;
	public static final byte TNew = 34;
	public static final byte TCapture = 35;
	public static final byte TTag = 36;
	public static final byte TReplace = 37;
	public static final byte TStart = 38;
	public static final byte TCommit = 39;
	public static final byte TAbort = 40;

	public static final byte TLookup = 41;
	public static final byte TMemo = 42;

	public static final byte SOpen = 43;
	public static final byte SClose = 44;
	public static final byte SMask = 45;
	public static final byte SDef = 46;
	public static final byte SIsDef = 47;
	public static final byte SExists = 48;
	public static final byte SMatch = 49;
	public static final byte SIs = 50;
	public static final byte SIsa = 51;
	public static final byte SDefNum = 52;
	public static final byte SCount = 53;
	public static final byte Exit = 54; // 7-bit only

	/* extended */
	public static final byte DFirst = 55; // Dfa
	public static final byte Cov = 56;
	public static final byte Covx = 57;

	public static final byte Label = 127; // 7-bit

	public static String stringfy(byte opcode) {
		switch (opcode) {
		case Nop:
			return "nop";
		case Fail:
			return "fail";
		case Alt:
			return "alt";
		case Succ:
			return "succ";
		case Jump:
			return "jump";
		case Call:
			return "call";
		case Ret:
			return "ret";
		case Pos:
			return "pos";
		case Back:
			return "back";
		case Skip:
			return "skip";

		case Byte:
			return "byte";
		case Any:
			return "any";
		case Str:
			return "str";
		case Set:
			return "set";

		case NByte:
			return "nbyte";
		case NAny:
			return "nany";
		case NStr:
			return "nstr";
		case NSet:
			return "nset";

		case OByte:
			return "obyte";
		case OAny:
			return "oany";
		case OStr:
			return "ostr";
		case OSet:
			return "oset";

		case RByte:
			return "rbyte";
		case RAny:
			return "rany";
		case RStr:
			return "rstr";
		case RSet:
			return "rset";

		case Consume:
			return "consume";
		case First:
			return "first";

		case Lookup:
			return "lookup";
		case Memo:
			return "memo";
		case MemoFail:
			return "memofail";

		case TPush:
			return "tpush";
		case TPop:
			return "tpop";
		case TLeftFold:
			return "tswap";
		case TNew:
			return "tnew";
		case TCapture:
			return "tcap";
		case TTag:
			return "ttag";
		case TReplace:
			return "trep";
		case TStart:
			return "tstart";
		case TCommit:
			return "tcommit";
		case TAbort:
			return "tabort";

		case TLookup:
			return "tlookup";
		case TMemo:
			return "tmemo";

		case SOpen:
			return "open";
		case SClose:
			return "close";
		case SMask:
			return "mask";
		case SDef:
			return "def";
		case SIsDef:
			return "isdef";
		case SExists:
			return "exists";
		case SIs:
			return "is";
		case SIsa:
			return "isa";
		case SDefNum:
			return "defnum";
		case SCount:
			return "count";

		case Exit:
			return "exit";

		default:
			return "-";
		}
	}

}
