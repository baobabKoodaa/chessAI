
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class YourEvaluator extends Evaluator {
    
    public static final int BLACK = 0;
    public static final int WHITE = 1;

    // Position specific
    int turn; // BLACK or WHITE
    boolean endgame;
    int[][] b;
    Double[][][] relativeSqValue;
    Square[] kingLives;
    int[][][] control;
    List<Square>[] pawns;
    List<Move> possibleCaptureMoves;
    List<Move> possibleCheckMoves;
    
    // Cleared at each eval call, modified inside minimax search within the check/capture tree
    HashMap<Long, Double> evaluatedPositions;
    
    // Set once or twice during a match
    int[] kingWithPawnsBonus;
    int[] kingEscapeRoutesBonus;
    int[][][] controlBonus;
    int enemyControlNearKingPenalty;
    
    // Set only once at match start
    Double[][] pieceValue;
    List<Square>[][] neighboringSquares;
    List<Square>[][] knightMoveLists;
    
    public YourEvaluator() {
        enemyControlNearKingPenalty = 10;
        generateNeighboringSquaresLists();
        generateKnightMoveLists();
        generateRookMoveLists();
        generateQueenMoveLists();
        endgame = false;
        generateKingWithPawnsBonus(endgame);
        generateKingEscapeRoutesBonus();
        generateControlBonus();
        generatePieceValues();
    }
    
    public double eval(Position p) {
        evaluatedPositions = new HashMap<>();
            // We need this because a check sequence can be infinite
            // Also, we don't have to search identical subtrees twice.
        return ev(p);
    }
    
    public double ev(Position p) {
        long thisPositionHash = hashPosition(p);
        if (evaluatedPositions.containsKey(thisPositionHash)) {
            return evaluatedPositions.get(thisPositionHash);
        }
        b = p.board;
        relativeSqValue = new Double[2][6][6];
        double v = 0;
        
        // initialize position specific static variables
        kingLives = new Square[2];
        control = new int[2][6][6];
        pawns = new ArrayList[2];
        pawns[BLACK] = new ArrayList<Square>(6);
        pawns[WHITE] = new ArrayList<Square>(6);
        turn = (p.whiteToMove ? WHITE : BLACK);
        possibleCaptureMoves = new ArrayList<>();
        possibleCheckMoves = new ArrayList<>();
        
        for (int x = 0; x < b.length; ++x) {
            for (int y = 0; y < b[x].length; ++y) {
                v += preProcessSquare(x,y);
            }
        }
        if (kingLives[WHITE] == null) return -1e9;
        if (kingLives[BLACK] == null) return 1e9;
        v += postProcessKing(WHITE, kingLives[WHITE]);
        v -= postProcessKing(BLACK, kingLives[BLACK]);

        for (int x=0; x<=5; x++) {
            for (int y=0; y<=5; y++) {
                //TODO: vaihda *1 -- sen mukaan ollaanko keskellä lautaa / vihun päässä, jne.
                v += control[WHITE][x][y] * relativeSqValue[WHITE][x][y] * 1;
                v -= control[BLACK][x][y] * relativeSqValue[BLACK][x][y] * 1;
            }
        }
        
        //TODO: if endgame == false && piececount < 8 -> muuta endgame=true ja REGEN JUTTUJA
        
        
        
        
        // Minimax into the capture/check tree, return min/max (depending on whose turn it is now)
        // of *exactly which positions??* from the tree. Consider null moves..?
        
        evaluatedPositions.put(thisPositionHash, v);
        return v;
    }
    
    public double preProcessSquare(int x, int y) {
        double v = 0;
        markDownSquareValue(x, y);
        switch (b[x][y]) {
            case Position.WKing:    v += preProcessKing(WHITE, x, y);break;
            case Position.WQueen:   v += preProcessQueen(WHITE, x, y);break;
            case Position.WKnight:  v += preProcessKnight(WHITE, x, y);break;
            case Position.WRook:    v += preProcessRook(WHITE, x, y);break;
            case Position.WPawn:    v += preProcessPawn(WHITE, x, y);break;

            case Position.BKing:    v -= preProcessKing(BLACK, x, y);break;
            case Position.BQueen:   v -= preProcessQueen(BLACK, x, y);break; 
            case Position.BKnight:  v -= preProcessKnight(BLACK, x, y);break;    
            case Position.BRook:    v -= preProcessRook(BLACK, x, y);break;
            case Position.BPawn:    v -= preProcessPawn(BLACK, x, y);break;    
            default:                ; break;
        }
        return v;
    }
        
    private void markDownSquareValue(int x, int y) {
        int unit = b[x][y];
        relativeSqValue[WHITE][x][y] = pieceValue[WHITE][unit];
        relativeSqValue[BLACK][x][y] = pieceValue[BLACK][unit];
    }
    
    /** NOTE: All preProcess -methods also
     *          - save control information
     *          - save possible capture moves.
     */
    
    /** Adds king-pawn bonuses. */
    public double preProcessKing(int color, int ax, int ay) {
        double v = 0;
        int neighboringPawnsCount = 0;
        int nontangentialEscapeRoutesCount = 0;
        boolean[] escapesX = new boolean[6];
        boolean[] escapesY = new boolean[6];
        for (Square neighbor : neighboringSquares[ax][ay]) {
            int x = neighbor.x;
            int y = neighbor.y;
            control[color][x][y]++;
            //TODO: Jos uhataan vihun nappia @x,y && vihulla ei controllia siihen ruutuun -> lisätään capturemoveseihin
            switch (color) {
                case BLACK:
                    if (Position.BPawn == b[x][y]) neighboringPawnsCount++;
                    break;
                case WHITE:
                    if (Position.WPawn == b[x][y]) neighboringPawnsCount++;
                    break;
                default:
                    break;
            }
        }

        v += kingWithPawnsBonus[neighboringPawnsCount];
        kingLives[color] = new Square(ax,ay);
        return v;
    }
    
    /** Adds score for non tangential escape routes for king,
        deducts score for enemy's control in king-adjacent squares. */
    public double postProcessKing(int color, Square loc) {
        double v = 0;
        int enemyColor = (color+1)%2;
        int ax = loc.x;
        int ay = loc.y;
        int nontangentialEscapeRoutesCount = 0;
        boolean[] escapesX = new boolean[6];
        boolean[] escapesY = new boolean[6];
        for (Square neighbor : neighboringSquares[ax][ay]) {
            int x = neighbor.x;
            int y = neighbor.y;
            if (Position.Empty == b[x][y]) {
                if (control[enemyColor][x][y] > 0) {
                    v -= enemyControlNearKingPenalty * control[enemyColor][x][y];
                    continue; // can't escape to here
                }
                if (escapesX[x] || escapesY[y]) continue; // already counted a tangential escape
                escapesX[x] = true;
                escapesY[y] = true;
                nontangentialEscapeRoutesCount++;
            }
        }
        v += kingEscapeRoutesBonus[nontangentialEscapeRoutesCount];
        return v;
    }
    
    private double preProcessRook(int color, int ax, int ay) {
        double v = 500;
        addControlsHorizontalVertical(color, ax, ay);
        return v;
    }

    private double preProcessQueen(int color, int ax, int ay) {
        double v = 900;
        addControlsHorizontalVertical(color, ax, ay);
        addControlsDiagonal(color, ax, ay);
        return v;
    }
    
    private double preProcessKnight(int color, int ax, int ay) {
        double v = 300;
        for (Square move : knightMoveLists[ax][ay]) {
            int x = move.x;
            int y = move.y;
            control[color][x][y]++;
        }
        return v;
    }

    private double preProcessPawn(int color, int ax, int ay) {
        // add to pawns list
        return 100;
    }

    
    
    private void addControlsHorizontalVertical(int color, int ax, int ay) {
        for (int x=ax-1; x>=0; x--) {
            control[color][x][ay]++;
            if (b[x][ay] != Position.Empty) break;
        }
        for (int x=ax+1; x<=5; x++) {
            control[color][x][ay]++;
            if (b[x][ay] != Position.Empty) break;
        }
        for (int y=ay-1; y>=0; y--) {
            control[color][ax][y]++;
            if (b[ax][y] != Position.Empty) break;
        }
        for (int y=ay+1; y<=5; y++) {
            control[color][ax][y]++;
            if (b[ax][y] != Position.Empty) break;
        }
    }
    
    private void addControlsDiagonal(int color, int ax, int ay) {
        int x = ax+1;
        int y = ay+1;
        while (x <= 5 && y <= 5) {
            control[color][x][y]++;
            if (b[x][y] != Position.Empty) break;
            x++;
            y++;
        }
        x = ax-1;
        y = ay-1;
        while (x >= 0 && y >= 0) {
            control[color][x][y]++;
            if (b[x][y] != Position.Empty) break;
            x--;
            y--;
        }
        x = ax+1;
        y = ay-1;
        while (x <= 5 && y >= 0) {
            control[color][x][y]++;
            if (b[x][y] != Position.Empty) break;
            x++;
            y--;
        }
        x = ax-1;
        y = ay+1;
        while (x >= 0 && y <= 5) {
            control[color][x][y]++;
            if (b[x][y] != Position.Empty) break;
            x--;
            y++;
        }
    }

    private void generateNeighboringSquaresLists() {
        neighboringSquares = new ArrayList[6][6];
        for (int x=0; x<6; x++) {
            for (int y=0; y<6; y++) {
                ArrayList<Square> list = new ArrayList<>(8);
                if (x-1 >= 0) {
                    list.add(new Square(x-1,y));
                    if (y-1 >= 0) list.add(new Square(x-1, y-1));
                    if (y+1 <= 5) list.add(new Square(x-1, y+1));
                }
                if (x+1 <= 5) {
                    list.add(new Square(x+1,y));
                    if (y-1 >= 0) list.add(new Square(x+1, y-1));
                    if (y+1 <= 5) list.add(new Square(x+1, y+1));
                }
                if (y-1 >= 0) list.add(new Square(x,y-1));
                if (y+1 <= 5) list.add(new Square(x,y+1));
                neighboringSquares[x][y] = list;
            }
        }
    }

    private void generateKingWithPawnsBonus(boolean endgame) {
        kingWithPawnsBonus = new int[9];
        if (endgame) {
            
        } else {
            //kingWithPawnsBonus[0] = 0;
            kingWithPawnsBonus[1] = 30;
            kingWithPawnsBonus[2] = 45;
            for (int i=3; i<=8; i++) kingWithPawnsBonus[i] = 50;
        }
    }


    private void generateKingEscapeRoutesBonus() {
        kingEscapeRoutesBonus = new int[8];
        //kingEscapeRoutesBonus[0] = 0;
        kingEscapeRoutesBonus[1] = 100;
        for (int i=2; i<=7; i++)
            kingEscapeRoutesBonus[i] = 120;
    }
    
    private void generateControlBonus() {
        controlBonus = new int[2][6][6];
        //TODO: päätä miten päin boardi on
    }

    private void generateKnightMoveLists() {
        knightMoveLists = new ArrayList[6][6];
        for (int x=0; x<6; x++) {
            for (int y=0; y<6; y++) {
                ArrayList<Square> list = new ArrayList<>(8);
                if (x-1 >= 0) {
                    if (y-2 >= 0) list.add(new Square(x-1, y-2));
                    if (y+2 <= 5) list.add(new Square(x-1, y+2));
                    if (x-2 >= 0) {
                        if (y-1 >= 0) list.add(new Square(x-2, y-1));
                        if (y+1 <= 5) list.add(new Square(x-2, y+1));
                    }
                }
                if (x+1 <= 5) {
                    if (y-2 >= 0) list.add(new Square(x+1, y-2));
                    if (y+2 <= 5) list.add(new Square(x+1, y+2));
                    if (x+2 <= 5) {
                        if (y-1 >= 0) list.add(new Square(x+2, y-1));
                        if (y+1 <= 5) list.add(new Square(x+2, y+1));
                    }
                }
                knightMoveLists[x][y] = list;
            }
        }
    }

    private void generateRookMoveLists() {
        
    }

    private void generateQueenMoveLists() {
        
    }
    
    private void generatePieceValues() {
        pieceValue = new Double[2][13];
        pieceValue[WHITE][Position.Empty] = 0.5; // controlling empty square
        pieceValue[BLACK][Position.Empty] = 0.5;
        pieceValue[WHITE][Position.WKing] = 0.3; // controlling sq with own king
        pieceValue[BLACK][Position.BKing] = 0.3;
        pieceValue[WHITE][Position.BKing] = 30.0; // Check
        pieceValue[BLACK][Position.WKing] = 30.0;
        pieceValue[WHITE][Position.WQueen] = 0.7; // Protecting queen
        pieceValue[BLACK][Position.BQueen] = 0.7;
        pieceValue[WHITE][Position.BQueen] = 20.0; // Threatening enemy queen
        pieceValue[BLACK][Position.WQueen] = 20.0;
        pieceValue[WHITE][Position.WRook] = 1.0; // Protecting rook (yes its better than protecting queen)
        pieceValue[BLACK][Position.BRook] = 1.0;
        pieceValue[WHITE][Position.BRook] = 12.0; // Threatening enemy rook
        pieceValue[BLACK][Position.WRook] = 12.0;
        pieceValue[WHITE][Position.WKnight] = 1.7; // Protecting knight
        pieceValue[BLACK][Position.BKnight] = 1.7;
        pieceValue[WHITE][Position.BKnight] = 8.0; // Threatening knight
        pieceValue[BLACK][Position.WKnight] = 8.0;
        pieceValue[WHITE][Position.WPawn] = 2.0; // Protecting pawn
        pieceValue[BLACK][Position.BPawn] = 2.0;
        pieceValue[WHITE][Position.BPawn] = 3.0;
        pieceValue[BLACK][Position.WPawn] = 3.0;
        
    }

    private long hashPosition(Position p) {
        // remember to consider whose turn it is, too
        return new Random().nextLong(); //TODO:
    }


}

class Square {
    int x;
    int y;

    public Square(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "      x = " + x + " , y = " + y;
    }
    
    
}

class Move {
    int x1;
    int y1;
    int x2;
    int y2;

    public Move(int x1, int y1, int x2, int y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }
    
}