package Evaluators;

import Framework.Position;
import Evaluators.Evaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class FoodChainV2Hash extends Evaluator {
    
    public static final int BLACK = 0;
    public static final int WHITE = 1;
    
    public static final double PVrook = 500;
    public static final double PVknight = 300;
    public static final double PVqueen = 900;
    public static final double PVpawn = 100;
    

    // Position specific
    int turn; // BLACK or WHITE
    boolean endgame;
    int pieceCount;
    int[] pieceValues;
    int[][] b;
    Double[][][] relativeSqValue;
    Square[] kingLives;
    int[][][] control;
    List<Square>[] pawns;
    
    // Cleared at each eval call, modified inside minimax search within the check/capture tree
    HashMap<Long, Double> evaluatedPositions;
    HashMap<Long, int[][]> collisionChecker;
    
    // Set once or twice during a match
    int[] kingEscapeRoutesBonus;
    double[][][] controlBonus;
    int enemyControlNearKingPenalty;
    double[][] pawnUpgradePotentialBonus;
    
    // Set only once at match start
    Double[][] pieceValue;
    List<Square>[][] neighboringSquares;
    List<Square>[][] knightMoveLists;
    Random rng;
    
    // Statistic collection variables
    public long countFoundHash;
    public long countNotFoundHash;
    
    public FoodChainV2Hash() {
        evaluatedPositions = new HashMap<>();
        collisionChecker = new HashMap<>();
        rng = new Random();
        endgame = false;
        enemyControlNearKingPenalty = 10;
        generatePawnUpgradePotentialBonuses();
        generateNeighboringSquaresLists();
        generateKnightMoveLists();
        generateKingEscapeRoutesBonus();
        generateControlBonus();
        generatePieceValues();
    }
    
    public double eval(Position p) {
        //if (rng.nextInt(10000) == 0) System.out.println("Hash table size " + evaluatedPositions.size() + " has helped " + (countFoundHash * 1.0 / (countNotFoundHash+countFoundHash)));
        return ev(p);
    }
    
    public double ev(Position p) {
        b = p.board;
        long thisPositionHash = hashPosition(p);
        if (evaluatedPositions.containsKey(thisPositionHash)) {
            return evaluatedPositions.get(thisPositionHash);
        }
        
        
        // initialize position specific static variables
        relativeSqValue = new Double[2][6][6];
        pieceCount = 36; // deduct empty squares to reach actual piececount
        pieceValues = new int[2];
        kingLives = new Square[2];
        control = new int[2][6][6];
        pawns = new ArrayList[2];
        pawns[BLACK] = new ArrayList<Square>(6);
        pawns[WHITE] = new ArrayList<Square>(6);
        turn = (p.whiteToMove ? WHITE : BLACK);
        
        //TODO: taulukko josta pelin vaiheen (pieceCount) ja yksikön tyypin (heppa) mukaan
        // pisteytys ruudulle jossa yksikkö on: v += t[BLACK][heppa][pieceCount][x][y]
        
        double v = 0;

        for (int x = 0; x < b.length; x++) {
            for (int y = 0; y < b[x].length; y++) {
                v += preProcessSquare(x,y);
            }
        }
        if (kingLives[WHITE] == null) return -1e9;
        if (kingLives[BLACK] == null) return 1e9;
        if (endgame = false && pieceCount < 13) {
            endgame = true;
            enemyControlNearKingPenalty = 0; // king becomes a playah!
        }
        
        //TODO: if (endGame == true && pieceCount > 13)
        
        
        //double v = pieceValues[WHITE] - pieceValues[BLACK] + pieceValueDiffBonus;
        v += postProcessKing(WHITE, kingLives[WHITE]);
        v -= postProcessKing(BLACK, kingLives[BLACK]);
        v += postProcessPawns(WHITE);
        v -= postProcessPawns(BLACK);

        for (int x=0; x<=5; x++) {
            for (int y=0; y<=5; y++) {
                // control = lkm uhkauksia ruutuun
                // relativeSqVal = ruudussa olevan uhkauksen/suojauksen arvo
                // controlBonus = ruudun arvo sijainnin mukaan, keskeltä/vihun päästä isompi arvo

                v += control[WHITE][x][y] * relativeSqValue[WHITE][x][y] * controlBonus[WHITE][x][y];
                v -= control[BLACK][x][y] * relativeSqValue[BLACK][x][y] * controlBonus[BLACK][x][y];
            }
        }
        
        
        //TODO: nappuloiden suhteelliset arvot; häviöllä oleva ei halua vaihtokauppoja!
        //  (endgames ei toimi ihan niin:
        //     jos esim. solttu+heppa vs. solttu jäljellä -> soltut vaihtoon -> tasuri)
        
        //TODO: pisteytys jäljellä olevien siirtojen lkm!
        //TODO: huom: blocked by chessmate! sillai blokatun yksikön arvo -50%?
        
        //TODO: Square-luokka pois!
        
        // Oisko naiivi capturesequence arviointi parempi kuin uus minmax?
        // Riippuen löytyykö huomattavaa eroa nappuloiden arvossa jossain capturesequencin
        // "päätöshaarassa", voitais joko arvioida se päätöshaara tai tämä mistä lähdettiin haarautumaan.
        // Tuleeko tässä ongelmia shakituksen kanssa?
        
        // Minimax into the capture/check tree, return min/max (depending on whose turn it is now)
        // of *exactly which positions??* from the tree. Consider null moves..?
        
        //TODO: MonteCarlo-simulointi oikeiden taikalukujen löytämiseksi annetuista rangeista?
        
        
        //Uncomment this in production
        v += rng.nextInt(10);
        
        evaluatedPositions.put(thisPositionHash, v);
        return v;
    }
    
    public long hashPosition(Position p) {
        long hash = (p.whiteToMove ? 2 : 1); // !
        long A = 31L;
        for (int y=0; y<6; y++) {
            for (int x=0; x<6; x++) {
                hash *= A; // modulates by long overflow
                hash += (b[x][y]+1);
            }
        }
        hash *= A;
        
        // Collision checker, remove from production
//        int[][] board = new int[6][6];
//        for (int y=0; y<6; y++) {
//            for (int x=0; x<6; x++) {
//                board[x][y] = b[x][y];
//            }
//        }
//        if (collisionChecker.containsKey(hash)) {
//            int[][] other = collisionChecker.get(hash);
//            for (int y=0; y<6; y++) {
//                for (int x=0; x<6; x++) {
//                    if (board[x][y] != other[x][y]) System.out.println("Collision!");
//                    //else System.out.println("ok");
//                }
//            }
//        }
//        collisionChecker.put(hash, board);
        
        return hash;
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
            default:                pieceCount--; break;
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
    
    /** Mark that king lives. */
    public double preProcessKing(int color, int ax, int ay) {
        double v = 0;
        for (Square neighbor : neighboringSquares[ax][ay]) {
            int x = neighbor.x;
            int y = neighbor.y;
            control[color][x][y]++;
            //TODO: Jos uhataan vihun nappia @x,y && vihulla ei controllia siihen ruutuun -> lisätään capturemoveseihin
            //TODO: Jos kuninkaalla ei laillisia siirtoja, penalty!
            switch (color) {
                case BLACK:
                    
                    break;
                case WHITE:
                    
                    break;
                default:
                    break;
            }
        }

        kingLives[color] = new Square(ax,ay);
        return v;
    }
    
    /** Adds score for non tangential escape routes for king,
        deducts score for enemy's control in king-adjacent squares,
        penalty for straying off before the endgame. */
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
            if (control[enemyColor][x][y] > 0) {
                v -= enemyControlNearKingPenalty * control[enemyColor][x][y];
                continue; // can't escape to here
            }
            if (b[x][y] != Position.Empty) continue; // can't escape to here
            if (escapesX[x] || escapesY[y]) continue; // already counted a tangential escape
            escapesX[x] = true;
            escapesY[y] = true;
            nontangentialEscapeRoutesCount++;
        }
        v += kingEscapeRoutesBonus[nontangentialEscapeRoutesCount];
        
        if (endgame) {
            // halutaan turvata solttujen nostamista
        } else {
            if (ay != 5 && ay != 0) {
                v -= 10; // penalty kuningas ei päädyssä
            } else {
                //TODO: escaperoutes tehokkaammin tähän lohkoon
                // tähän samalla myös "solttukilvestä" bonusta?
                // ideaali: 2 solttua kuninkaan edessä, viistosti sivulle 1 vapaa ruutu?
            }
        }
        return v;
    }
    
    private double preProcessRook(int color, int ax, int ay) {
        double v = PVrook;
        addControlsHorizontalVertical(color, ax, ay);
        return v;
    }

    private double preProcessQueen(int color, int ax, int ay) {
        double v = PVqueen;
        addControlsHorizontalVertical(color, ax, ay);
        addControlsDiagonal(color, ax, ay);
        return v;
    }
    
    private double preProcessKnight(int color, int ax, int ay) {
        double v = PVknight;
        for (Square move : knightMoveLists[ax][ay]) {
            int x = move.x;
            int y = move.y;
            control[color][x][y]++;
        }
        return v;
    }

    private double preProcessPawn(int color, int ax, int ay) {
        pawns[color].add(new Square(ax, ay));
        return PVpawn;
    }
    
    //TODO: Mieti solttujen pisteytys uusiks! Ainakin upgrade incentive
    // progressiiviseks endgame-editymisen mukaan sen taulukon avulla
    //Non-connectaavista soltuista penaltyä?
    
    /** Endgame incentive to upgrade pawns + penalty for same lane pawns. */
    private double postProcessPawns(int color) {
        double v = 0;
        if (endgame) {
            for (Square pawn : pawns[color]) {
                v += pawnUpgradePotentialBonus[color][pawn.y];
            }
        }
        boolean[] laneAlreadyHasAPawn = new boolean[6];
        for (Square pawn : pawns[color]) {
            if (laneAlreadyHasAPawn[pawn.x]) v -= 30;
            laneAlreadyHasAPawn[pawn.x] = true;
        }
        return v;
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

    private void generateKingEscapeRoutesBonus() {
        kingEscapeRoutesBonus = new int[8];
        //kingEscapeRoutesBonus[0] = 0;
        kingEscapeRoutesBonus[1] = 100;
        for (int i=2; i<=7; i++)
            kingEscapeRoutesBonus[i] = 120;
    }
    
    private void generateControlBonus() {
        controlBonus = new double[2][6][6];
        for (int x=0; x<=5; x++) controlBonus[WHITE][0][x] = 1;
        for (int x=0; x<=5; x++) controlBonus[WHITE][1][x] = 1.05;
        for (int x=0; x<=5; x++) controlBonus[WHITE][2][x] = 1.1;
        for (int x=0; x<=5; x++) controlBonus[WHITE][3][x] = 1.2;
        for (int x=0; x<=5; x++) controlBonus[WHITE][4][x] = 1.2;
        for (int x=0; x<=5; x++) controlBonus[WHITE][5][x] = 1.15;
        for (int x=0; x<=5; x++) controlBonus[BLACK][5][x] = 1;
        for (int x=0; x<=5; x++) controlBonus[BLACK][4][x] = 1.05;
        for (int x=0; x<=5; x++) controlBonus[BLACK][3][x] = 1.1;
        for (int x=0; x<=5; x++) controlBonus[BLACK][2][x] = 1.2;
        for (int x=0; x<=5; x++) controlBonus[BLACK][1][x] = 1.2;
        for (int x=0; x<=5; x++) controlBonus[BLACK][0][x] = 1.15;
        // Keskustabonukset
        controlBonus[WHITE][2][2] += 0.2;
        controlBonus[WHITE][3][2] += 0.2;
        controlBonus[WHITE][3][3] += 0.2;
        controlBonus[WHITE][2][3] += 0.2;
        controlBonus[BLACK][2][2] += 0.2;
        controlBonus[BLACK][3][2] += 0.2;
        controlBonus[BLACK][3][3] += 0.2;
        controlBonus[BLACK][2][3] += 0.2;
        // Suburb bonukset
        controlBonus[WHITE][1][1] += 0.1;
        controlBonus[WHITE][1][2] += 0.1;
        controlBonus[WHITE][1][3] += 0.1;
        controlBonus[WHITE][1][4] += 0.1;
        controlBonus[WHITE][2][4] += 0.1;
        controlBonus[WHITE][3][4] += 0.1;
        controlBonus[WHITE][4][4] += 0.1;
        controlBonus[WHITE][4][3] += 0.1;
        controlBonus[WHITE][4][2] += 0.1;
        controlBonus[WHITE][4][1] += 0.1;
        controlBonus[WHITE][3][1] += 0.1;
        controlBonus[WHITE][2][1] += 0.1;
        controlBonus[BLACK][1][1] += 0.1;
        controlBonus[BLACK][1][2] += 0.1;
        controlBonus[BLACK][1][3] += 0.1;
        controlBonus[BLACK][1][4] += 0.1;
        controlBonus[BLACK][2][4] += 0.1;
        controlBonus[BLACK][3][4] += 0.1;
        controlBonus[BLACK][4][4] += 0.1;
        controlBonus[BLACK][4][3] += 0.1;
        controlBonus[BLACK][4][2] += 0.1;
        controlBonus[BLACK][4][1] += 0.1;
        controlBonus[BLACK][3][1] += 0.1;
        controlBonus[BLACK][2][1] += 0.1;
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

    private void generatePawnUpgradePotentialBonuses() {
        pawnUpgradePotentialBonus = new double[2][6];
        pawnUpgradePotentialBonus[WHITE][2] = 10;
        pawnUpgradePotentialBonus[WHITE][3] = 40;
        pawnUpgradePotentialBonus[WHITE][4] = 100;
        
        pawnUpgradePotentialBonus[BLACK][3] = 10;
        pawnUpgradePotentialBonus[BLACK][2] = 40;
        pawnUpgradePotentialBonus[BLACK][1] = 100;
    }

    private class Square {
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
}