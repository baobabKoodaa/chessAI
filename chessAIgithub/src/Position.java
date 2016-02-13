import GUI.BoardController;
import java.util.Vector;

public class Position {
        int board [][];
	boolean whiteToMove;
        public double cachedResult;
    int winner = 0; // white = +1, black = -1
	
    final static int bRows = 6;
    final static int bCols = 6;


	final static int Empty = 0;
	final static int WKing = 1;
	final static int WQueen = 2;
	final static int WRook = 3;
	final static int WBishop = 4;
	final static int WKnight = 5;
	final static int WPawn = 6;
	final static int BKing = 7;
	final static int BQueen = 8;
	final static int BRook = 9;
	final static int BBishop = 10;
	final static int BKnight = 11;
	final static int BPawn = 12;
	
    	final int Nx [] = {-2,-2,-1,-1,1,1,2,2};
    	final int Ny [] = {1,-1,2,-2,2,-2,1,-1};
    	final int Bx [][] = {{1,2,3,4,5,6,7},{1,2,3,4,5,6,7},{-1,-2,-3,-4,-5,-6,-7},{-1,-2,-3,-4,-5,-6,-7}};
    	final int By [][] = {{1,2,3,4,5,6,7},{-1,-2,-3,-4,-5,-6,-7},{1,2,3,4,5,6,7},{-1,-2,-3,-4,-5,-6,-7}};
    	final int Rx [][] = {{0,0,0,0,0,0,0},{0,0,0,0,0,0,0},{1,2,3,4,5,6,7},{-1,-2,-3,-4,-5,-6,-7}};
    	final int Ry [][] = {{1,2,3,4,5,6,7},{-1,-2,-3,-4,-5,-6,-7},{0,0,0,0,0,0,0},{0,0,0,0,0,0,0}};
    	final int Kx [] = {1,1,1,0,0,-1,-1,-1};
    	final int Ky [] = {1,0,-1,1,-1,1,0,-1};
    
	public Position() {
		this.board = new int[bCols][bRows];
		this.whiteToMove = true;
                this.cachedResult = Double.NaN;
	}
	
	public void setStartingPosition() {
		for(int x = 0; x < bCols; ++x) {
			this.board[x][1] = WPawn;
			this.board[x][bRows-2] = BPawn;
			if(x == 0 || x == bCols-1) {
				this.board[x][0] = WRook;
				this.board[x][bRows-1] = BRook;
			} else if(x == 1 || x == bCols-2) {
				this.board[x][0] = WKnight;
				this.board[x][bRows-1] = BKnight;
			} else if(bCols == 8 && (x == 2 || x == bCols-3)) {
			    this.board[x][0] = WBishop;
			    this.board[x][bRows-1] = BBishop;
			} else if((bCols == 8 && x == 3) || (bCols == 6 && x == 2)) {
				this.board[x][0] = WQueen;
				this.board[x][bRows-1] = BQueen;
			} else if(x == bCols/2) {
				this.board[x][0] = WKing;
				this.board[x][bRows-1] = BKing;
			}
		}
	}
	
	void cloneEssentialsFrom(Position p) {
		for(int i = 0; i < this.board.length; ++i) {
			for(int j = 0; j < this.board[i].length; ++j) {
				this.board[i][j] = p.board[i][j];
			}
		}
		this.whiteToMove = !p.whiteToMove;
	}
        
        Position mirror() {
            Position p = new Position();
            for (int i = 0; i < this.board.length; ++i) {
                for (int j = 0; j < this.board[i].length; ++j) {
                    int piece = this.board[i][5 - j];
                    if (piece != 0)
                        p.board[i][j] = (piece >= 7 ? -6 : 6) + piece;
                }
            }
            p.whiteToMove = !this.whiteToMove;
            return p;
        }
	
	public void print(BoardController c) {
                // print to GUI
                String[][] b = new String[6][6];
		for(int y = bRows-1; y >= 0; --y) {
			for(int x = 0; x < bCols; ++x) {
                                int v = this.board[x][y];
				if(v == Empty)      b[x][y] = null;
				if(v == WKing)      b[x][y] = "wk";
				if(v == WQueen)     b[x][y] = "wq";
				if(v == WRook)      b[x][y] = "wr";
				if(v == WKnight)    b[x][y] = "wn";
				if(v == WPawn)      b[x][y] = "wp";
				if(v == BKing)      b[x][y] = "bk";
				if(v == BQueen)     b[x][y] = "bq";
				if(v == BRook)      b[x][y] = "br";
				if(v == BKnight)    b[x][y] = "bn";
				if(v == BPawn)      b[x][y] = "bp";
                        }
                }
                c.addNewPosition(b);
	}
	
	public boolean isValidPosition(Position p) {
		return true;
	}
	
	public static boolean isWhitePiece(int pval) {
		if(pval == 0) return false;
		if(pval < 7) return true;
		return false;
	}
	
	public static boolean isBlackPiece(int pval) {
		if(pval == 0) return false;
		if(pval > 6) return true;
		return false;
	}
	
	private static boolean isInsideBoard(int x, int y) {
		if(x < 0 || x >= bCols) return false;
		if(y < 0 || y >= bRows) return false;
		return true;
	}
	
	private boolean squaresContainSameColoredPieces(int x, int y, int x2, int y2) {
		if(isWhitePiece(this.board[x][y]) && isWhitePiece(this.board[x2][y2])) return true;
		if(isBlackPiece(this.board[x][y]) && isBlackPiece(this.board[x2][y2])) return true;
		return false;
	}

    private int checkWin(int x, int y) {
	// this is a piece just about to be captured.
	// if white king, black wins, and vice versa
	if (this.board[x][y] == WKing) return -1;
	if (this.board[x][y] == BKing) return +1;
	return 0;
    }
	
	public Vector<Position> getNextPositions() {
		Vector<Position> ret = new Vector<Position>();
		
		for(int x = 0; x < this.board.length; ++x) {
			for(int y = 0; y < this.board[x].length; ++y) {
				int pval = this.board[x][y];
				
				if(pval == Empty) continue;
				if(this.whiteToMove != isWhitePiece(pval)) continue;

				/* PIECE SPECIFIC STUFF */
				
				if(pval == WKing || pval == BKing) {
					for(int i = 0; i < Kx.length; ++i) {
						int x2 = Kx[i] + x;
						int y2 = Ky[i] + y;
						if(!isInsideBoard(x2,y2)) continue;
						if(squaresContainSameColoredPieces(x,y,x2,y2)) continue;
						Position p = new Position();
						p.cloneEssentialsFrom(this);
						p.winner = checkWin(x2,y2);
						p.board[x2][y2] = this.board[x][y];
						p.board[x][y] = Empty;
						ret.addElement(p);
					}
					continue;
				}
				
				if(pval == WQueen || pval == BQueen) {
					// Queens can move like bishops:
					for(int i = 0; i < Bx.length; ++i) {
						// for all the directions
						for(int j = 0; j < Bx[i].length; ++j) {
							// once a direction is obstructed, finish!!
							int x2 = Bx[i][j] + x;
							int y2 = By[i][j] + y;
							if(!isInsideBoard(x2,y2)) break;
							if(squaresContainSameColoredPieces(x,y,x2,y2)) break;
							
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x2,y2);
							p.board[x2][y2] = this.board[x][y];
							p.board[x][y] = Empty;
							ret.addElement(p);
							
							if(this.board[x2][y2] != Empty) {
								// ate it, and finished direction
								break;
							}
						}
					}
					
					// Queens can also move like rooks:
					
					for(int i = 0; i < Rx.length; ++i) {
						// for all the directions
						for(int j = 0; j < Rx[i].length; ++j) {
							// once a direction is obstructed, finish!!
							int x2 = Rx[i][j] + x;
							int y2 = Ry[i][j] + y;
							if(!isInsideBoard(x2,y2)) break;
							if(squaresContainSameColoredPieces(x,y,x2,y2)) break;
							
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x2,y2);
							p.board[x2][y2] = this.board[x][y];
							p.board[x][y] = Empty;
							ret.addElement(p);
							
							if(this.board[x2][y2] != Empty) {
								// ate it, and finished direction
								break;
							}
						}
					}
					continue;
				}
				
				if(pval == WRook || pval == BRook) {
					for(int i = 0; i < Rx.length; ++i) {
						// for all the directions
						for(int j = 0; j < Rx[i].length; ++j) {
							// once a direction is obstructed, finish!!
							int x2 = Rx[i][j] + x;
							int y2 = Ry[i][j] + y;
							if(!isInsideBoard(x2,y2)) break;
							if(squaresContainSameColoredPieces(x,y,x2,y2)) break;
							
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x2,y2);
							p.board[x2][y2] = this.board[x][y];
							p.board[x][y] = Empty;
							ret.addElement(p);
							
							if(this.board[x2][y2] != Empty) {
								// ate it, and finished direction
								break;
							}
						}
					}
					continue;
				}
				
				if(pval == WBishop || pval == BBishop) {
					for(int i = 0; i < Bx.length; ++i) {
						// for all the directions
						for(int j = 0; j < Bx[i].length; ++j) {
							// once a direction is obstructed, finish!!
							int x2 = Bx[i][j] + x;
							int y2 = By[i][j] + y;
							if(!isInsideBoard(x2,y2)) break;
							if(squaresContainSameColoredPieces(x,y,x2,y2)) break;
							
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x2,y2);
							p.board[x2][y2] = this.board[x][y];
							p.board[x][y] = Empty;
							ret.addElement(p);
							
							if(this.board[x2][y2] != Empty) {
								// ate it, and finished direction
								break;
							}
						}
					}
					continue;
				}
				
				if(pval == WKnight || pval == BKnight) {
					for(int i = 0; i < Nx.length; ++i) {
						int x2 = Nx[i] + x;
						int y2 = Ny[i] + y;
						if(!isInsideBoard(x2,y2)) continue;
						if(squaresContainSameColoredPieces(x,y,x2,y2)) continue;
						Position p = new Position();
						p.cloneEssentialsFrom(this);
						p.winner = checkWin(x2,y2);
						p.board[x2][y2] = this.board[x][y];
						p.board[x][y] = Empty;
						ret.addElement(p);
					}
					continue;
				}
				
				if(pval == WPawn) {
					boolean allowedMoves [] = new boolean [4];
					// 1 step forward
					allowedMoves[0] = isInsideBoard(x,y+1) && this.board[x][y+1] == Empty;
					// 2 steps forward (not in Los Alamos chess)
					allowedMoves[1] = bRows == 8 && isInsideBoard(x,y+2) && y == 1 && allowedMoves[0] && this.board[x][y+2] == Empty;
					// eat left
					allowedMoves[2] = isInsideBoard(x-1,y+1) && isBlackPiece(this.board[x-1][y+1]);
					// eat right
					allowedMoves[3] = isInsideBoard(x+1,y+1) && isBlackPiece(this.board[x+1][y+1]);

					if(allowedMoves[0]) {
						if(y+1 != bRows-1) {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.board[x][y+1] = pval;
							p.board[x][y] = Empty;
							ret.addElement(p);
						} else {

							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.board[x][y+1] = WKnight;
							p.board[x][y] = Empty;
							ret.addElement(p);
							if (bCols == 8) {
							    p = new Position();
							    p.cloneEssentialsFrom(this);
							    p.board[x][y+1] = WBishop;
							    p.board[x][y] = Empty;
							    ret.addElement(p);
							}
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.board[x][y+1] = WRook;
							p.board[x][y] = Empty;
							ret.addElement(p);
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.board[x][y+1] = WQueen;
							p.board[x][y] = Empty;
							ret.addElement(p);
						}
					}
					
					if(allowedMoves[1]) {
						Position p = new Position();
						p.cloneEssentialsFrom(this);
						p.board[x][y+2] = pval;
						p.board[x][y] = Empty;
						ret.addElement(p);
					}
					
					if(allowedMoves[2]) {
						if(y+1 != bRows-1) {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x-1,y+1);
							p.board[x-1][y+1] = pval;
							p.board[x][y] = Empty;
							ret.addElement(p);
						} else {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x-1,y+1);
							p.board[x-1][y+1] = WKnight;
							p.board[x][y] = Empty;
							ret.addElement(p);
							if (bCols == 8) {
							    p = new Position();
							    p.cloneEssentialsFrom(this);
							    p.winner = checkWin(x-1,y+1);
							    p.board[x-1][y+1] = WBishop;
							    p.board[x][y] = Empty;
							    ret.addElement(p);
							}
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x-1,y+1);
							p.board[x-1][y+1] = WRook;
							p.board[x][y] = Empty;
							ret.addElement(p);
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x-1,y+1);
							p.board[x-1][y+1] = WQueen;
							p.board[x][y] = Empty;
							ret.addElement(p);
						}
					}
					
					if(allowedMoves[3]) {
						if(y+1 != bRows-1) {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x+1,y+1);
							p.board[x+1][y+1] = pval;
							p.board[x][y] = Empty;
							ret.addElement(p);
						} else {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x+1,y+1);
							p.board[x+1][y+1] = WKnight;
							p.board[x][y] = Empty;
							ret.addElement(p);
							if (bCols == 8) {
							    p = new Position();
							    p.cloneEssentialsFrom(this);
							    p.winner = checkWin(x+1,y+1);
							    p.board[x+1][y+1] = WBishop;
							    p.board[x][y] = Empty;
							    ret.addElement(p);
							}
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x+1,y+1);
							p.board[x+1][y+1] = WRook;
							p.board[x][y] = Empty;
							ret.addElement(p);
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x+1,y+1);
							p.board[x+1][y+1] = WQueen;
							p.board[x][y] = Empty;
							ret.addElement(p);
						}
					}
					
					continue;
				}
				
				if(pval == BPawn) {
					boolean allowedMoves [] = new boolean [4];
					// 1 step forward
					allowedMoves[0] = isInsideBoard(x,y-1) && this.board[x][y-1] == Empty;
					// 2 steps forward (not in Los Alamos chess)
					allowedMoves[1] = bRows == 8 &&  isInsideBoard(x,y-2) && y == 6 && allowedMoves[0] && this.board[x][y-2] == Empty;
					// eat right
					allowedMoves[2] = isInsideBoard(x-1,y-1) && isWhitePiece(this.board[x-1][y-1]);
					// eat left
					allowedMoves[3] = isInsideBoard(x+1,y-1) && isWhitePiece(this.board[x+1][y-1]);

					if(allowedMoves[0]) {
						if(y-1 != 0) {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.board[x][y-1] = pval;
							p.board[x][y] = Empty;
							ret.addElement(p);
						} else {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.board[x][y-1] = BKnight;
							p.board[x][y] = Empty;
							ret.addElement(p);
							if (bCols == 8) {
							    p = new Position();
							    p.cloneEssentialsFrom(this);
							    p.board[x][y-1] = BBishop;
							    p.board[x][y] = Empty;
							    ret.addElement(p);
							}
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.board[x][y-1] = BRook;
							p.board[x][y] = Empty;
							ret.addElement(p);
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.board[x][y-1] = BQueen;
							p.board[x][y] = Empty;
							ret.addElement(p);
						}
					}
					
					if(allowedMoves[1]) {
						Position p = new Position();
						p.cloneEssentialsFrom(this);
						p.board[x][y-2] = pval;
						p.board[x][y] = Empty;
						ret.addElement(p);
					}
					
					if(allowedMoves[2]) {
						if(y-1 != 0) {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x-1,y-1);
							p.board[x-1][y-1] = pval;
							p.board[x][y] = Empty;
							ret.addElement(p);
						} else {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x-1,y-1);
							p.board[x-1][y-1] = BKnight;
							p.board[x][y] = Empty;
							ret.addElement(p);
							if (bCols == 8) {
							    p = new Position();
							    p.cloneEssentialsFrom(this);
							    p.winner = checkWin(x-1,y-1);
							    p.board[x-1][y-1] = BBishop;
							    p.board[x][y] = Empty;
							    ret.addElement(p);
							}
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x-1,y-1);
							p.board[x-1][y-1] = BRook;
							p.board[x][y] = Empty;
							ret.addElement(p);
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x-1,y-1);
							p.board[x-1][y-1] = BQueen;
							p.board[x][y] = Empty;
							ret.addElement(p);
						}
					}
					
					if(allowedMoves[3]) {
						if(y-1 != 0) {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x+1,y-1);
							p.board[x+1][y-1] = pval;
							p.board[x][y] = Empty;
							ret.addElement(p);
						} else {
							Position p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x+1,y-1);
							p.board[x+1][y-1] = BKnight;
							p.board[x][y] = Empty;
							ret.addElement(p);
							if (bCols == 8) {
							    p = new Position();
							    p.cloneEssentialsFrom(this);
							    p.winner = checkWin(x+1,y-1);
							    p.board[x+1][y-1] = BBishop;
							    p.board[x][y] = Empty;
							    ret.addElement(p);
							}
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x+1,y-1);
							p.board[x+1][y-1] = BRook;
							p.board[x][y] = Empty;
							ret.addElement(p);
							p = new Position();
							p.cloneEssentialsFrom(this);
							p.winner = checkWin(x+1,y-1);
							p.board[x+1][y-1] = BQueen;
							p.board[x][y] = Empty;
							ret.addElement(p);
						}
					}
					
					continue;
				}
			}
		}
		
		return ret;
	}
}
