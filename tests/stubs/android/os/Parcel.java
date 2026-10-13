package android.os;
import java.util.ArrayList;
public class Parcel {
    private final ArrayList<Object> values = new ArrayList<>(); private int position;
    public void writeString(String s) { values.add(s); }
    public void writeInt(int i) { values.add(i); }
    public String readString() { return (String)values.get(position++); }
    public int readInt() { return (Integer)values.get(position++); }
    public void writeNoException() {}
    public void enforceInterface(String s) { if (!s.equals(readString())) throw new SecurityException(); }
}
