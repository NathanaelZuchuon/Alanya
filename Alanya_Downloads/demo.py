import numpy as np
import matplotlib.pyplot as plt
from matplotlib.widgets import Button

def cubic_spline_interpolation(x, y):
    """
    Calcule les coefficients des splines cubiques pour interpoler les points (x, y).
    
    Paramètres:
    x : Liste ou array des coordonnées x des points
    y : Liste ou array des coordonnées y des points
    
    Retour:
    a, b, c, d : Les coefficients des splines cubiques
    """
    # Vérification qu'il y a au moins 2 points
    if len(x) < 2:
        return None, None, None, None
    
    n = len(x) - 1  # Nombre d'intervalles
    
    # Conversion en arrays numpy
    x = np.array(x)
    y = np.array(y)
    
    # Tri des points selon x croissant
    idx = np.argsort(x)
    x = x[idx]
    y = y[idx]
    
    # Initialisation des coefficients
    a = y.copy()  # Coefficient a = f(x)
    b = np.zeros(n)
    c = np.zeros(n+1)
    d = np.zeros(n)
    
    # Si un seul intervalle, interpolation linéaire
    if n == 1:
        b[0] = (y[1] - y[0]) / (x[1] - x[0])
        return a, b, c, d
    
    # Calcul des différences h entre les points x
    h = np.diff(x)
    
    # Construction du système tridiagonal pour calculer les c
    # Ax = B où A est tridiagonal
    A = np.zeros((n+1, n+1))
    B = np.zeros(n+1)
    
    # Première et dernière lignes pour spline naturelle (c[0] = c[n] = 0)
    A[0, 0] = 1
    A[n, n] = 1
    
    # Construction de la matrice tridiagonale
    for i in range(1, n):
        A[i, i-1] = h[i-1]
        A[i, i] = 2 * (h[i-1] + h[i])
        A[i, i+1] = h[i]
        
        B[i] = 3 * ((y[i+1] - y[i]) / h[i] - (y[i] - y[i-1]) / h[i-1])
    
    # Résolution du système pour trouver c
    c = np.linalg.solve(A, B)
    
    # Calcul des coefficients b et d à partir de c
    for i in range(n):
        b[i] = (y[i+1] - y[i]) / h[i] - h[i] * (2 * c[i] + c[i+1]) / 3
        d[i] = (c[i+1] - c[i]) / (3 * h[i])
    
    return a, b, c, d

def evaluate_spline(x_points, x_eval, a, b, c, d):
    """
    Évalue la spline cubique sur les points x_eval.
    
    Paramètres:
    x_points : Points d'origine x utilisés pour calculer la spline
    x_eval : Points où évaluer la spline
    a, b, c, d : Coefficients des splines cubiques
    
    Retour:
    y_eval : Valeurs de la spline aux points x_eval
    """
    if a is None or len(x_points) < 2:
        return np.zeros_like(x_eval)
        
    y_eval = np.zeros_like(x_eval, dtype=float)
    
    for i, xi in enumerate(x_eval):
        # Trouver l'intervalle approprié
        idx = np.searchsorted(x_points, xi) - 1
        
        # Traiter les cas limites
        if idx < 0:
            idx = 0
        if idx >= len(x_points) - 1:
            idx = len(x_points) - 2
            
        # Calculer la différence entre xi et le point x précédent
        dx = xi - x_points[idx]
        
        # Évaluer le polynôme cubique
        y_eval[i] = a[idx] + b[idx] * dx + c[idx] * dx**2 + d[idx] * dx**3
    
    return y_eval

class InteractiveSplineBuilder:
    def __init__(self):
        self.fig, self.ax = plt.subplots(figsize=(10, 8))
        self.ax.set_title('Interpolation par spline cubique - Cliquez pour ajouter des points')
        self.ax.set_xlabel('x')
        self.ax.set_ylabel('y')
        self.ax.grid(True)
        
        # Limites initiales du graphique
        self.ax.set_xlim(-1, 10)
        self.ax.set_ylim(-5, 5)
        
        # Stockage des points
        self.x = []
        self.y = []
        self.points_plot = None
        self.spline_plot = None
        
        # Connecter les événements
        self.cid = self.fig.canvas.mpl_connect('button_press_event', self.onclick)
        
        # Ajouter des boutons
        axclear = plt.axes([0.7, 0.01, 0.1, 0.05])
        self.bclear = Button(axclear, 'Effacer')
        self.bclear.on_clicked(self.clear)
        
        axinterpolate = plt.axes([0.81, 0.01, 0.15, 0.05])
        self.binterpolate = Button(axinterpolate, 'Interpoler')
        self.binterpolate.on_clicked(self.interpolate)
        
        # Message d'instructions
        self.ax.text(0.5, 0.95, 'Cliquez pour ajouter des points\nUtilisez les boutons pour effacer ou interpoler',
                     transform=self.ax.transAxes, ha='center',
                     bbox=dict(facecolor='white', alpha=0.8))
    
    def onclick(self, event):
        # Vérifier que le clic est dans la zone du graphique
        if event.inaxes != self.ax:
            return
        
        # Ajouter le point
        self.x.append(event.xdata)
        self.y.append(event.ydata)
        
        # Mettre à jour l'affichage des points
        self.update()
    
    def update(self):
        # Supprimer les anciens tracés
        if self.points_plot:
            self.points_plot.remove()
        
        # Tracer les nouveaux points
        if len(self.x) > 0:
            self.points_plot = self.ax.scatter(self.x, self.y, color='red', s=50)
            
            # Numéroter les points
            for i, (xi, yi) in enumerate(zip(self.x, self.y)):
                self.ax.annotate(f"{i+1}", (xi, yi), xytext=(5, 5), 
                                textcoords='offset points')
        
        # Rafraîchir l'affichage
        self.fig.canvas.draw()
    
    def interpolate(self, event):
        # Vérifier qu'il y a assez de points pour l'interpolation
        if len(self.x) < 2:
            self.ax.set_title('Ajoutez au moins 2 points pour l\'interpolation')
            self.fig.canvas.draw()
            return
        
        # Tri des points selon x
        indices = np.argsort(self.x)
        sorted_x = [self.x[i] for i in indices]
        sorted_y = [self.y[i] for i in indices]
        
        # Calculer les coefficients de la spline
        a, b, c, d = cubic_spline_interpolation(sorted_x, sorted_y)
        
        # Supprimer l'ancien tracé de la spline s'il existe
        if self.spline_plot:
            self.spline_plot.remove()
        
        # Générer des points pour tracer la courbe
        x_fine = np.linspace(min(self.x), max(self.x), 500)
        y_fine = evaluate_spline(sorted_x, x_fine, a, b, c[:len(a)], d)
        
        # Tracer la spline
        self.spline_plot = self.ax.plot(x_fine, y_fine, 'b-', label='Spline cubique')[0]
        
        # Mettre à jour le titre et la légende
        self.ax.set_title(f'Interpolation par spline cubique - {len(self.x)} points')
        self.ax.legend()
        
        # Afficher les coefficients dans la console
        print("\nCoefficients des splines cubiques:")
        for i in range(len(sorted_x)-1):
            print(f"Intervalle [{sorted_x[i]:.2f}, {sorted_x[i+1]:.2f}]:")
            print(f"  a = {a[i]:.6f}")
            print(f"  b = {b[i]:.6f}")
            print(f"  c = {c[i]:.6f}")
            print(f"  d = {d[i]:.6f}")
        
        # Rafraîchir l'affichage
        self.fig.canvas.draw()
    
    def clear(self, event):
        # Réinitialiser les listes de points
        self.x = []
        self.y = []
        
        # Supprimer tous les tracés
        if self.points_plot:
            self.points_plot.remove()
            self.points_plot = None
        
        if self.spline_plot:
            self.spline_plot.remove()
            self.spline_plot = None
        
        # Réinitialiser le titre
        self.ax.set_title('Interpolation par spline cubique - Cliquez pour ajouter des points')
        
        # Supprimer la légende
        self.ax.legend_.remove() if hasattr(self.ax, 'legend_') and self.ax.legend_ else None
        
        # Rafraîchir l'affichage
        self.fig.canvas.draw()

# Créer et afficher l'interface interactive
if __name__ == "__main__":
    builder = InteractiveSplineBuilder()
    plt.tight_layout()
    plt.subplots_adjust(bottom=0.15)  # Faire de la place pour les boutons
    plt.show()
