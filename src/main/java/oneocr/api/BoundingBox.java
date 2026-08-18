package oneocr.api;

/**
 * Represents a bounding box with 4 corner coordinates for rotated rectangles
 */
public record BoundingBox(
    double x1, double y1,
    double x2, double y2, 
    double x3, double y3,
    double x4, double y4
) {
    
    /**
     * Get the minimum bounding rectangle (axis-aligned)
     */
    public Rectangle getAxisAlignedBounds() {
        double minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
        double minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        double maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
        double maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
        
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }
    
    public double[]bounds(){
        return new double[]{x1,y1,x2,y2,x3,y3,x4,y4};
    }
    
    public record Rectangle(double x, double y, double width, double height) {}
}