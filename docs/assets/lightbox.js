// Enhanced lightbox functionality for documentation screenshots
(function() {
  'use strict';

  let currentImageIndex = 0;
  let allImages = [];
  let currentSet = [];

  // Create enhanced lightbox DOM structure
  const createLightbox = () => {
    const lightbox = document.createElement('div');
    lightbox.className = 'lightbox';
    lightbox.id = 'screenshot-lightbox';
    lightbox.innerHTML = `
      <div class="lightbox-content">
        <button class="lightbox-nav prev" aria-label="Image précédente" title="Précédent">‹</button>
        <div class="lightbox-image-container">
          <img id="lightbox-image" src="" alt="" />
        </div>
        <button class="lightbox-nav next" aria-label="Image suivante" title="Suivant">›</button>
        <button class="lightbox-close" aria-label="Fermer">&times;</button>
        <div class="lightbox-counter" id="lightbox-counter"></div>
      </div>
    `;
    document.body.appendChild(lightbox);
    return lightbox;
  };

  // Create SVG placeholder for missing images
  const createPlaceholder = () => {
    return `
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" style="width: 100%; height: 100%; display: block;">
        <rect width="200" height="200" fill="#444"/>
        <text x="100" y="90" font-family="Inter, sans-serif" font-size="14" fill="#888" text-anchor="middle" font-weight="600">Image non</text>
        <text x="100" y="110" font-family="Inter, sans-serif" font-size="14" fill="#888" text-anchor="middle" font-weight="600">disponible</text>
      </svg>
    `;
  };

  // Handle image loading error and show placeholder
  const handleImageError = (img) => {
    img.classList.add('placeholder');
    img.style.background = '#444';
    const parent = img.parentElement;
    if (parent && img.alt) {
      parent.setAttribute('data-placeholder', 'true');
    }
  };

  // Update navigation buttons state
  const updateNavButtons = (lightbox) => {
    const prevBtn = lightbox.querySelector('.lightbox-nav.prev');
    const nextBtn = lightbox.querySelector('.lightbox-nav.next');

    if (currentSet.length <= 1) {
      prevBtn.style.display = 'none';
      nextBtn.style.display = 'none';
    } else {
      prevBtn.style.display = 'flex';
      nextBtn.style.display = 'flex';
      prevBtn.disabled = currentImageIndex === 0;
      nextBtn.disabled = currentImageIndex === currentSet.length - 1;
    }

    // Update counter
    if (currentSet.length > 1) {
      const counter = lightbox.querySelector('#lightbox-counter');
      counter.textContent = `${currentImageIndex + 1} / ${currentSet.length}`;
      counter.style.display = 'block';
    } else {
      lightbox.querySelector('#lightbox-counter').style.display = 'none';
    }
  };

  // Show specific image in lightbox
  const showImage = (index, lightbox) => {
    if (index < 0 || index >= currentSet.length) return;
    currentImageIndex = index;
    const img = currentSet[index];
    const lightboxImage = lightbox.querySelector('#lightbox-image');
    lightboxImage.src = img.src;
    lightboxImage.alt = img.alt;
    updateNavButtons(lightbox);
  };

  // Get all images in the same container group
  const getImagesInGroup = (targetImg) => {
    const container = targetImg.closest('.instrument-screenshots');
    if (!container) return [targetImg];
    return Array.from(container.querySelectorAll('img.shot'));
  };

  // Initialize lightbox
  const init = () => {
    let lightbox = document.getElementById('screenshot-lightbox');
    if (!lightbox) {
      lightbox = createLightbox();
    }

    const lightboxImage = lightbox.querySelector('#lightbox-image');
    const prevBtn = lightbox.querySelector('.lightbox-nav.prev');
    const nextBtn = lightbox.querySelector('.lightbox-nav.next');
    const closeButton = lightbox.querySelector('.lightbox-close');

    // Add click handlers to all screenshot images
    const screenshots = document.querySelectorAll('img.shot');
    screenshots.forEach(img => {
      // Handle missing images
      img.addEventListener('error', () => handleImageError(img));

      // Handle click to open lightbox
      img.addEventListener('click', (e) => {
        e.stopPropagation();
        currentSet = getImagesInGroup(img);
        currentImageIndex = currentSet.indexOf(img);
        showImage(currentImageIndex, lightbox);
        lightbox.classList.add('active');
      });
    });

    // Navigation: Previous button
    prevBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (currentImageIndex > 0) {
        showImage(currentImageIndex - 1, lightbox);
      }
    });

    // Navigation: Next button
    nextBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (currentImageIndex < currentSet.length - 1) {
        showImage(currentImageIndex + 1, lightbox);
      }
    });

    // Close on × button
    closeButton.addEventListener('click', (e) => {
      e.stopPropagation();
      lightbox.classList.remove('active');
    });

    // Close on background click (but not on buttons or image)
    lightbox.addEventListener('click', (e) => {
      if (e.target === lightbox) {
        lightbox.classList.remove('active');
      }
    });

    // Close on Escape key
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && lightbox.classList.contains('active')) {
        lightbox.classList.remove('active');
      }
      // Arrow key navigation
      if (lightbox.classList.contains('active')) {
        if (e.key === 'ArrowLeft' && currentImageIndex > 0) {
          e.preventDefault();
          showImage(currentImageIndex - 1, lightbox);
        } else if (e.key === 'ArrowRight' && currentImageIndex < currentSet.length - 1) {
          e.preventDefault();
          showImage(currentImageIndex + 1, lightbox);
        }
      }
    });

    // Touch swipe support (mobile)
    let touchStartX = 0;
    let touchEndX = 0;

    lightbox.addEventListener('touchstart', (e) => {
      touchStartX = e.changedTouches[0].screenX;
    }, false);

    lightbox.addEventListener('touchend', (e) => {
      touchEndX = e.changedTouches[0].screenX;
      handleSwipe();
    }, false);

    const handleSwipe = () => {
      const swipeThreshold = 50;
      const diff = touchStartX - touchEndX;

      if (Math.abs(diff) > swipeThreshold) {
        if (diff > 0 && currentImageIndex < currentSet.length - 1) {
          // Swiped left: next image
          showImage(currentImageIndex + 1, lightbox);
        } else if (diff < 0 && currentImageIndex > 0) {
          // Swiped right: previous image
          showImage(currentImageIndex - 1, lightbox);
        }
      }
    };
  };

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
