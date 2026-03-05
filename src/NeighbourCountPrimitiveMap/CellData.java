package NeighbourCountPrimitiveMap;

public class CellData {
    int neighbourCount;
    boolean state;

    public CellData(int count, boolean state){
        this.neighbourCount = count;
        this.state = state;
    }

    public CellData(){

    }
}
